#!/usr/bin/env node
/**
 * Boot Admin 项目初始化器。
 * 仅依赖 Node.js 标准库，默认预览；传 --apply 才会写入目标目录。
 */
import { cp, mkdir, readFile, readdir, rename, rm, stat, writeFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';

const root = path.resolve(import.meta.dirname, '..');
const args = process.argv.slice(2);
const option = (name) => {
  const index = args.indexOf(name);
  return index >= 0 ? args[index + 1] : undefined;
};
const configPath = option('--config');
const targetArg = option('--target');
const apply = args.includes('--apply');

if (!configPath || !targetArg) {
  console.error('用法: node .boot-admin/init-project.mjs --config project.yml --target ../new-project [--apply]');
  process.exit(1);
}

function parseYaml(source) {
  const values = {};
  for (const line of source.split(/\r?\n/)) {
    const match = line.match(/^([A-Za-z][\w-]*):\s*(.*?)\s*$/);
    if (match) values[match[1]] = match[2].replace(/^['"]|['"]$/g, '');
  }
  return values;
}

const config = parseYaml(await readFile(path.resolve(configPath), 'utf8'));
const required = ['projectName', 'projectCode', 'javaPackage', 'companyName', 'databaseName'];
const missing = required.filter((key) => !config[key]);
if (missing.length || !/^[a-z][a-z0-9-]*$/.test(config.projectCode || '')
  || !/^[a-z][\w.]*$/.test(config.javaPackage || '') || !/^[a-z][a-z0-9_]*$/.test(config.databaseName || '')) {
  console.error(`配置无效。必填: ${required.join(', ')}；projectCode 使用小写字母、数字和连字符。`);
  process.exit(1);
}

const pascalCase = config.projectCode.split(/[-_]/).map((part) => part[0].toUpperCase() + part.slice(1)).join('');
const packagePath = config.javaPackage.replaceAll('.', '/');
const replacements = [
  // `boot-admin` is also part of the official Spring Boot Admin artifact name;
  // protect that dependency from the project-name replacement below.
  ['spring-boot-admin', 'spring-__boot_admin_dependency__'],
  ['Spring Boot Admin', 'Spring __BOOT_ADMIN_PRODUCT__'],
  ['Boot Admin', config.projectName],
  ['boot-admin', config.projectCode],
  ['boot_admin', config.databaseName],
  ['com.boot.cloud', config.javaPackage],
  ['com/boot/cloud', packagePath],
  ['BootAdmin', pascalCase],
  ['boot-boot', `${config.projectCode}-boot`],
  ['Your Company', config.companyName],
  ['spring-__boot_admin_dependency__', 'spring-boot-admin'],
  ['Spring __BOOT_ADMIN_PRODUCT__', 'Spring Boot Admin'],
];
const ignored = new Set(['.git', 'node_modules', 'target', 'dist', 'build', '.turbo', '.idea', 'logs']);
const target = path.resolve(targetArg);

async function walk(directory) {
  const result = [];
  for (const entry of await readdir(directory)) {
    if (ignored.has(entry)) continue;
    const absolute = path.join(directory, entry);
    const info = await stat(absolute);
    if (info.isDirectory()) result.push(...await walk(absolute));
    else result.push(absolute);
  }
  return result;
}

async function walkEntries(directory) {
  const result = [];
  for (const entry of await readdir(directory)) {
    if (ignored.has(entry)) continue;
    const absolute = path.join(directory, entry);
    if ((await stat(absolute)).isDirectory()) {
      result.push(...await walkEntries(absolute));
    }
    result.push(absolute);
  }
  return result;
}

const files = await walk(root);
const changes = [];
for (const file of files) {
  const relative = path.relative(root, file);
  if (relative.startsWith('.boot-admin/')) continue;
  let output = relative;
  for (const [from, to] of replacements) output = output.replaceAll(from, to);
  let content;
  try { content = await readFile(file, 'utf8'); } catch { continue; }
  let replaced = content;
  for (const [from, to] of replacements) replaced = replaced.replaceAll(from, to);
  if (output !== relative || replaced !== content) changes.push(relative);
}

console.log(`${apply ? '将初始化' : '预览'} ${target}`);
console.log(`项目: ${config.projectName} (${config.projectCode})，包名: ${config.javaPackage}，数据库: ${config.databaseName}`);
console.log(`将处理 ${changes.length} 个文件。${apply ? '' : ' 未写入任何文件；确认后附加 --apply。'}`);
if (!apply) process.exit(0);

await mkdir(target, { recursive: true });
await cp(root, target, { recursive: true, filter: (source) => !ignored.has(path.basename(source)) });
for (const source of await walkEntries(target)) {
  const relative = path.relative(target, source);
  if (relative === '.boot-admin' || relative.startsWith('.boot-admin/')) continue;
  let output = relative;
  for (const [from, to] of replacements) output = output.replaceAll(from, to);
  if (output !== relative) {
    const destination = path.join(target, output);
    await mkdir(path.dirname(destination), { recursive: true });
    const sourceInfo = await stat(source);
    let destinationExists = false;
    try {
      await stat(destination);
      destinationExists = true;
    } catch {}
    if (sourceInfo.isDirectory() && destinationExists) await rm(source, { recursive: true, force: true });
    else await rename(source, destination);
  }
}
for (const sourceFile of await walk(target)) {
  const relative = path.relative(target, sourceFile);
  if (relative.startsWith('.boot-admin/')) continue;
  let content;
  try { content = await readFile(sourceFile, 'utf8'); } catch { continue; }
  let replaced = content;
  for (const [from, to] of replacements) replaced = replaced.replaceAll(from, to);
  if (replaced !== content) await writeFile(sourceFile, replaced, 'utf8');
}
console.log('初始化完成。请按 docs/项目初始化指南.md 执行数据库与构建验证。');
