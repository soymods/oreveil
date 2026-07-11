#!/usr/bin/env node

import { createHash } from "node:crypto";
import { createWriteStream, existsSync, mkdirSync, readFileSync, readdirSync, renameSync, rmSync, statSync, writeFileSync } from "node:fs";
import { copyFile, mkdir, readFile, writeFile } from "node:fs/promises";
import { get } from "node:https";
import { createServer } from "node:net";
import { basename, join, resolve } from "node:path";
import { spawn, spawnSync } from "node:child_process";

const root = resolve(new URL("..", import.meta.url).pathname);
const props = readProperties(join(root, "gradle.properties"));
const minecraftVersion = process.env.PAPER_VERSION ?? props.minecraft_version ?? "1.21";
const archivesBaseName = props.archives_base_name ?? "oreveil";
const pluginVersion = props.plugin_version ?? "0.1.3";
let targetName;
try {
  targetName = normalizeTarget(process.env.OREVEIL_TARGET ?? targetForMinecraftVersion(minecraftVersion));
} catch (error) {
  console.error(error.message);
  process.exit(1);
}
const buildTask = "build";
let serverPort = process.env.SERVER_PORT ?? "25565";
const serverDir = join(root, "build", "dev-server", safePathSegment(minecraftVersion));
const pluginsDir = join(serverDir, "plugins");
const paperJar = join(serverDir, "paper.jar");
const protocolLibJar = join(pluginsDir, "ProtocolLib.jar");
const protocolLibMarker = join(pluginsDir, ".ProtocolLib.version");
const pluginJar = join(root, "build", "libs", `${archivesBaseName}-${pluginVersion}.jar`);
const deployedPluginJar = join(pluginsDir, `${archivesBaseName}.jar`);

const args = new Set(process.argv.slice(2));
const watchMode = args.has("--watch") || args.has("watch");
const noBuild = args.has("--no-build");
const resetWorld = args.has("--reset-world");
const prepareOnly = args.has("--prepare-only");
let serverJava = null;
let serverProcess = null;
let restarting = false;
let restartTimer = null;
let stdinWired = false;

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});

async function main() {
  if (!prepareOnly) {
    serverJava = selectJavaRuntime(targetName);
    await selectServerPort();
  }
  await prepareServer();
  if (resetWorld) {
    resetWorldFolders();
  }
  await buildAndDeploy();
  if (prepareOnly) {
    console.log(`Prepared dev server in ${serverDir}`);
    return;
  }
  if (watchMode) {
    watchSources();
  }
  startServer();

  process.on("SIGINT", () => stopAndExit());
  process.on("SIGTERM", () => stopAndExit());
}

async function prepareServer() {
  await mkdir(pluginsDir, { recursive: true });
  await ensurePaper();
  await ensureProtocolLib();
  await writeServerFiles();
}

async function selectServerPort() {
  if (process.env.SERVER_PORT) {
    return;
  }

  const preferred = Number(serverPort);
  const available = await firstAvailablePort(preferred, 20);
  serverPort = String(available);
  if (available !== preferred) {
    console.log(`Port ${preferred} is in use; using ${available} for this dev server.`);
  }
}

async function buildAndDeploy() {
  if (!noBuild) {
    await runCommand("./gradlew", [buildTask, "-q"], root);
  }
  await mkdir(pluginsDir, { recursive: true });
  await copyFile(pluginJar, deployedPluginJar);
  console.log(`Deployed ${basename(pluginJar)} as ${basename(deployedPluginJar)} to ${pluginsDir}`);
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
  const protocolLib = protocolLibForTarget(targetName);
  if (existsSync(protocolLibJar) && existsSync(protocolLibMarker) && (await readFile(protocolLibMarker, "utf8")).trim() === protocolLib.version) {
    return;
  }

  const url = process.env.PROTOCOLLIB_URL ?? protocolLib.url;
  console.log(`Downloading ProtocolLib ${protocolLib.version}...`);
  await downloadFile(url, protocolLibJar);
  await writeFile(protocolLibMarker, protocolLib.version);
}

async function writeServerFiles() {
  await writeFile(join(serverDir, "eula.txt"), "eula=true\n");
  await writeFile(join(serverDir, "server.properties"), [
    `server-port=${serverPort}`,
    `motd=Oreveil ${minecraftVersion} ${targetName} on ${serverPort}`,
    "online-mode=false",
    "white-list=false",
    "spawn-protection=0",
    `query.port=${serverPort}`,
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
  console.log(`Starting Paper ${minecraftVersion} dev server with Oreveil target ${targetName} using Java ${serverJava.major}. Join localhost:${serverPort}.`);
  serverProcess = spawn(serverJava.bin, ["-Xms1G", "-Xmx2G", "-jar", "paper.jar", "nogui"], {
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

function targetForMinecraftVersion(version) {
  const match = /^(\d+)\.(\d+)(?:\.(\d+))?/.exec(version);
  if (!match) {
    throw new Error(`Could not parse Minecraft version '${version}'.`);
  }

  const major = Number(match[1]);
  const minor = Number(match[2]);
  const patch = match[3] == null ? 0 : Number(match[3]);
  if (major === 1 && minor === 16) {
    return "paper-1.16.x";
  }
  if (major === 1 && minor === 17) {
    return "paper-1.17.x";
  }
  if (major === 1 && minor === 18) {
    return "paper-1.18.x";
  }
  if (major === 1 && minor === 19) {
    return "paper-1.19.x";
  }
  if (major === 1 && minor === 20 && patch <= 4) {
    return "paper-1.20.0-1.20.4";
  }
  if (major === 1 && minor === 20 && patch <= 6) {
    return "paper-1.20.5-1.20.6";
  }
  if (major === 1 && minor === 21) {
    return "paper-1.21";
  }
  if (major === 26) {
    return "paper-26.x";
  }
  throw new Error(
    `No Oreveil target is defined for Minecraft ${version}.`
      + " Supported versions are 1.16.x, 1.17.x, 1.18.x, 1.19.x, 1.20.x, 1.21.x, and 26.x."
  );
}

function normalizeTarget(target) {
  if (target === "paper-1.16") {
    return "paper-1.16.x";
  }
  if (target === "paper-1.18") {
    return "paper-1.18.x";
  }
  if (target === "paper-1.20.5" || target === "paper-1.20.6") {
    return "paper-1.20.5-1.20.6";
  }
  if (target === "paper-26") {
    return "paper-26.x";
  }
  return target;
}

function protocolLibForTarget(target) {
  if (target === "paper-1.16.x" || target === "paper-1.17.x") {
    return {
      version: "4.8.0",
      url: "https://github.com/dmulloy2/ProtocolLib/releases/download/4.8.0/ProtocolLib.jar",
    };
  }
  return {
    version: "5.4.0",
    url: "https://github.com/dmulloy2/ProtocolLib/releases/download/5.4.0/ProtocolLib.jar",
  };
}

function selectJavaRuntime(target) {
  const requirement = javaRequirementForTarget(target);
  if (requirement == null) {
    return { bin: process.env.JAVA_BIN ?? "java", major: "default" };
  }

  const candidates = javaCandidates(requirement.major);
  const checked = [];
  for (const candidate of candidates) {
    const major = javaMajor(candidate.bin);
    if (major == null) {
      continue;
    }
    checked.push(`${candidate.label}: Java ${major}`);
    if (matchesJavaRequirement(major, requirement)) {
      return { bin: candidate.bin, major };
    }
  }

  const details = checked.length > 0 ? ` Checked: ${checked.join(", ")}.` : "";
  const versionText = requirement.exact ? `Java ${requirement.major}` : `Java ${requirement.major} or newer`;
  throw new Error(
    `Oreveil target ${target} needs ${versionText} to run its Paper dev server.`
      + ` Set JAVA_BIN to a matching Java executable, or set JAVA${requirement.major}_HOME/JAVA_${requirement.major}_HOME.`
      + details
  );
}

function javaRequirementForTarget(target) {
  if (target === "paper-1.16.x" || target === "paper-1.17.x") {
    return { major: 16, exact: true };
  }
  if (target === "paper-1.18.x" || target === "paper-1.19.x" || target === "paper-1.20.0-1.20.4") {
    return { major: 17, exact: false };
  }
  if (target === "paper-1.20.5-1.20.6" || target === "paper-1.21") {
    return { major: 21, exact: false };
  }
  if (target === "paper-26.x") {
    return { major: 25, exact: false };
  }
  return null;
}

function matchesJavaRequirement(actualMajor, requirement) {
  return requirement.exact ? actualMajor === requirement.major : actualMajor >= requirement.major;
}

function javaCandidates(required) {
  const candidates = [];
  addJavaCandidate(candidates, process.env.JAVA_BIN, "JAVA_BIN");
  addJavaHomeCandidate(candidates, process.env[`JAVA${required}_HOME`], `JAVA${required}_HOME`);
  addJavaHomeCandidate(candidates, process.env[`JAVA_${required}_HOME`], `JAVA_${required}_HOME`);
  addJavaHomeCandidate(candidates, process.env[`JAVA_HOME_${required}`], `JAVA_HOME_${required}`);
  addJavaHomeCandidate(candidates, process.env.JAVA_HOME, "JAVA_HOME");
  addJavaHomeCandidate(candidates, macJavaHome(required), `/usr/libexec/java_home -v ${required}`);
  addJavaCandidate(candidates, "java", "PATH java");
  return candidates;
}

function addJavaHomeCandidate(candidates, home, label) {
  if (home) {
    addJavaCandidate(candidates, join(home.trim(), "bin", "java"), label);
  }
}

function addJavaCandidate(candidates, bin, label) {
  if (!bin) {
    return;
  }
  if (candidates.some((candidate) => candidate.bin === bin)) {
    return;
  }
  candidates.push({ bin, label });
}

function macJavaHome(required) {
  const result = spawnSync("/usr/libexec/java_home", ["-v", String(required)], { encoding: "utf8" });
  if (result.status !== 0) {
    return null;
  }
  return result.stdout.trim();
}

function javaMajor(bin) {
  const result = spawnSync(bin, ["-version"], { encoding: "utf8" });
  if (result.status !== 0) {
    return null;
  }
  const output = `${result.stdout}\n${result.stderr}`;
  const match = /version "([^"]+)"/.exec(output);
  if (!match) {
    return null;
  }
  const version = match[1];
  if (version.startsWith("1.")) {
    return Number(version.split(".")[1]);
  }
  return Number(version.split(".")[0]);
}

function safePathSegment(value) {
  return value.replace(/[^A-Za-z0-9._-]/g, "_");
}

async function firstAvailablePort(start, attempts) {
  for (let offset = 0; offset < attempts; offset++) {
    const port = start + offset;
    if (await isPortAvailable(port)) {
      return port;
    }
  }
  throw new Error(`No available port found from ${start} to ${start + attempts - 1}. Set SERVER_PORT to choose one explicitly.`);
}

function isPortAvailable(port) {
  return new Promise((resolvePort) => {
    const server = createServer();
    server.once("error", () => resolvePort(false));
    server.once("listening", () => {
      server.close(() => resolvePort(true));
    });
    server.listen(port, "0.0.0.0");
  });
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
