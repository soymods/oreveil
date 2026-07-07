#!/usr/bin/env node

import { createHash } from "node:crypto";
import { createWriteStream, existsSync, mkdirSync, readFileSync, readdirSync, renameSync, rmSync, statSync, writeFileSync } from "node:fs";
import { copyFile, mkdir, readFile, writeFile } from "node:fs/promises";
import { get } from "node:https";
import { basename, join, resolve } from "node:path";
import { spawn } from "node:child_process";

const root = resolve(new URL("..", import.meta.url).pathname);
const props = readProperties(join(root, "gradle.properties"));
const minecraftVersion = process.env.PAPER_VERSION ?? props.minecraft_version ?? "1.21";
const archivesBaseName = props.archives_base_name ?? "oreveil";
const pluginVersion = props.plugin_version ?? "0.1.0-SNAPSHOT";
const targetName = process.env.OREVEIL_TARGET ?? "paper-1.21";
const serverDir = join(root, "build", "dev-server");
const pluginsDir = join(serverDir, "plugins");
const paperJar = join(serverDir, "paper.jar");
const protocolLibJar = join(pluginsDir, "ProtocolLib.jar");
const pluginJar = join(root, "build", "libs", `${archivesBaseName}-${targetName}-${pluginVersion}.jar`);
const deployedPluginJar = join(pluginsDir, `${archivesBaseName}.jar`);

const args = new Set(process.argv.slice(2));
const watchMode = args.has("--watch") || args.has("watch");
const noBuild = args.has("--no-build");
const resetWorld = args.has("--reset-world");
const prepareOnly = args.has("--prepare-only");
let serverProcess = null;
let restarting = false;
let restartTimer = null;
let stdinWired = false;

await prepareServer();
if (resetWorld) {
  resetWorldFolders();
}
await buildAndDeploy();
if (prepareOnly) {
  console.log(`Prepared dev server in ${serverDir}`);
  process.exit(0);
}
if (watchMode) {
  watchSources();
}
startServer();

process.on("SIGINT", () => stopAndExit());
process.on("SIGTERM", () => stopAndExit());

async function prepareServer() {
  await mkdir(pluginsDir, { recursive: true });
  await ensurePaper();
  await ensureProtocolLib();
  await writeServerFiles();
}

async function buildAndDeploy() {
  if (!noBuild) {
    await runCommand("./gradlew", ["build", "-q"], root);
  }
  await mkdir(pluginsDir, { recursive: true });
  await copyFile(pluginJar, deployedPluginJar);
  console.log(`Deployed ${basename(deployedPluginJar)} to ${pluginsDir}`);
}

async function ensurePaper() {
  if (existsSync(paperJar)) {
    return;
  }

  console.log(`Downloading Paper ${minecraftVersion}...`);
  const overrideUrl = process.env.PAPER_URL;
  if (overrideUrl) {
    await downloadFile(overrideUrl, paperJar);
    return;
  }

  const builds = await fetchJson(`https://fill.papermc.io/v3/projects/paper/versions/${minecraftVersion}/builds`);
  const latest = builds.find((build) => build.channel === "STABLE") ?? builds[0];
  const download = latest?.downloads?.["server:default"];
  if (!download?.url) {
    throw new Error(`No Paper server download found for Minecraft ${minecraftVersion}.`);
  }
  await downloadFile(download.url, paperJar);
}

async function ensureProtocolLib() {
  if (existsSync(protocolLibJar)) {
    return;
  }

  const url = process.env.PROTOCOLLIB_URL ?? "https://github.com/dmulloy2/ProtocolLib/releases/download/5.4.0/ProtocolLib.jar";
  console.log("Downloading ProtocolLib...");
  await downloadFile(url, protocolLibJar);
}

async function writeServerFiles() {
  await writeFile(join(serverDir, "eula.txt"), "eula=true\n");
  await writeFile(join(serverDir, "server.properties"), [
    "server-port=25565",
    "motd=Oreveil dev server",
    "online-mode=false",
    "white-list=false",
    "spawn-protection=0",
    "gamemode=creative",
    "difficulty=peaceful",
    "view-distance=8",
    "simulation-distance=6",
    "allow-flight=true",
    "enable-command-block=true",
    "",
  ].join("\n"));

  const username = process.env.MC_USERNAME;
  if (username && !existsSync(join(serverDir, "ops.json"))) {
    await writeFile(join(serverDir, "ops.json"), JSON.stringify([
      {
        uuid: offlineUuid(username),
        name: username,
        level: 4,
        bypassesPlayerLimit: true,
      },
    ], null, 2));
  }
}

function startServer() {
  console.log(`Starting Paper dev server. Join localhost:25565 from Minecraft ${minecraftVersion}.`);
  serverProcess = spawn("java", ["-Xms1G", "-Xmx2G", "-jar", "paper.jar", "nogui"], {
    cwd: serverDir,
    stdio: ["pipe", "inherit", "inherit"],
  });
  wireStdin();
  serverProcess.on("exit", (code, signal) => {
    serverProcess = null;
    if (restarting) {
      restarting = false;
      return;
    }
    if (watchMode) {
      console.log(`Server stopped (${signal ?? code}). Waiting for changes; press Ctrl+C to exit.`);
      return;
    }
    process.exit(code ?? 0);
  });
}

function wireStdin() {
  if (stdinWired) {
    return;
  }
  stdinWired = true;
  process.stdin.resume();
  process.stdin.on("data", (chunk) => {
    if (serverProcess?.stdin?.writable) {
      serverProcess.stdin.write(chunk);
    }
  });
}

function watchSources() {
  console.log("Watching src/, build.gradle.kts, and gradle.properties. Changes rebuild and restart the dev server.");
  const watched = new Map();
  setInterval(() => {
    const changed = findChangedFiles([
      join(root, "src"),
      join(root, "build.gradle.kts"),
      join(root, "gradle.properties"),
      join(root, "settings.gradle.kts"),
    ], watched);
    if (changed.length > 0) {
      scheduleRestart();
    }
  }, 1000);
}

function scheduleRestart() {
  clearTimeout(restartTimer);
  restartTimer = setTimeout(async () => {
    if (restarting) {
      return;
    }
    restarting = true;
    console.log("Changes detected. Rebuilding and restarting...");
    if (serverProcess) {
      serverProcess.stdin.write("stop\n");
      await waitForExit(serverProcess);
    }
    try {
      await buildAndDeploy();
    } catch (error) {
      console.error(error.message);
      restarting = false;
      return;
    }
    restarting = false;
    startServer();
  }, 500);
}

function resetWorldFolders() {
  for (const name of ["world", "world_nether", "world_the_end"]) {
    const path = join(serverDir, name);
    if (existsSync(path)) {
      rmSync(path, { recursive: true, force: true });
    }
  }
}

async function stopAndExit() {
  if (serverProcess) {
    serverProcess.stdin.write("stop\n");
    await waitForExit(serverProcess);
  }
  process.exit(0);
}

function waitForExit(child) {
  return new Promise((resolveWait) => child.once("exit", resolveWait));
}

function runCommand(command, commandArgs, cwd) {
  return new Promise((resolveRun, rejectRun) => {
    const child = spawn(command, commandArgs, { cwd, stdio: "inherit" });
    child.on("exit", (code) => {
      if (code === 0) {
        resolveRun();
      } else {
        rejectRun(new Error(`${command} ${commandArgs.join(" ")} failed with exit code ${code}.`));
      }
    });
  });
}

function readProperties(path) {
  const result = {};
  for (const line of readFileSync(path, "utf8").split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) {
      continue;
    }
    const index = trimmed.indexOf("=");
    if (index > 0) {
      result[trimmed.slice(0, index).trim()] = trimmed.slice(index + 1).trim();
    }
  }
  return result;
}

function fetchJson(url) {
  return new Promise((resolveFetch, rejectFetch) => {
    get(url, { headers: { "User-Agent": "Oreveil dev-server" } }, (response) => {
      if (response.statusCode >= 300 && response.statusCode < 400 && response.headers.location) {
        resolveFetch(fetchJson(response.headers.location));
        return;
      }
      if (response.statusCode !== 200) {
        rejectFetch(new Error(`GET ${url} failed with HTTP ${response.statusCode}`));
        return;
      }
      let body = "";
      response.setEncoding("utf8");
      response.on("data", (chunk) => body += chunk);
      response.on("end", () => resolveFetch(JSON.parse(body)));
    }).on("error", rejectFetch);
  });
}

function downloadFile(url, destination) {
  return new Promise((resolveDownload, rejectDownload) => {
    const request = get(url, { headers: { "User-Agent": "Oreveil dev-server" } }, (response) => {
      if (response.statusCode >= 300 && response.statusCode < 400 && response.headers.location) {
        resolveDownload(downloadFile(response.headers.location, destination));
        return;
      }
      if (response.statusCode !== 200) {
        rejectDownload(new Error(`Download failed with HTTP ${response.statusCode}: ${url}`));
        return;
      }
      const temp = `${destination}.tmp`;
      const file = createWriteStream(temp);
      response.pipe(file);
      file.on("finish", () => {
        file.close();
        renameSync(temp, destination);
        resolveDownload();
      });
      file.on("error", rejectDownload);
    });
    request.on("error", rejectDownload);
  });
}

function findChangedFiles(paths, known) {
  const changed = [];
  for (const path of paths) {
    scan(path, known, changed);
  }
  return changed;
}

function scan(path, known, changed) {
  if (!existsSync(path)) {
    return;
  }
  const stat = statSync(path);
  if (stat.isDirectory()) {
    for (const entry of readdirSync(path)) {
      scan(join(path, entry), known, changed);
    }
    return;
  }
  const marker = `${stat.mtimeMs}:${stat.size}`;
  if (!known.has(path)) {
    known.set(path, marker);
    return;
  }
  if (known.get(path) !== marker) {
    known.set(path, marker);
    changed.push(path);
  }
}

function offlineUuid(username) {
  const hash = createHash("md5").update(`OfflinePlayer:${username}`, "utf8").digest();
  hash[6] = (hash[6] & 0x0f) | 0x30;
  hash[8] = (hash[8] & 0x3f) | 0x80;
  const hex = hash.toString("hex");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
