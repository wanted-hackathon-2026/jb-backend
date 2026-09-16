const { test } = require("node:test");
const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { join } = require("node:path");
const vm = require("node:vm");

const source = readFileSync(join(__dirname, "../../main/resources/swagger/google-login.js"), "utf8");

function browser() {
  const element = () => ({
    style: {}, children: [], events: {}, dataset: {},
    append(...children) { this.children.push(...children); },
    before() {}, setAttribute() {},
    addEventListener(event, handler) { this.events[event] = handler; }
  });
  const panel = element();
  panel.dataset = { clientId: "public-client-id", contextPath: "/dev" };
  const head = element();
  const calls = [];
  const timers = new Map();
  let callback;
  let nextTimer = 0;
  const window = {
    addEventListener(_event, handler) { this.load = handler; },
    setTimeout(handler, delay) { timers.set(++nextTimer, { handler, delay }); return nextTimer; },
    clearTimeout(id) { timers.delete(id); },
    ui: {
      preauthorizeApiKey(...args) { calls.push(["authorize", ...args]); },
      authActions: { logout(...args) { calls.push(["clear", ...args]); } }
    },
    google: { accounts: { id: {
      initialize(config) { callback = config.callback; },
      renderButton() {}, disableAutoSelect() { calls.push(["disableAutoSelect"]); }
    } } }
  };
  const requests = [];
  const responses = [];
  vm.runInNewContext(source, {
    window,
    document: { head, createElement: element, getElementById: (id) => id === "swagger-google-login" ? panel : element() },
    async fetch(url, options) {
      requests.push({ url, options });
      const response = responses.shift();
      if (response instanceof Error) throw response;
      return response;
    }
  });
  window.load();
  const library = head.children[0];
  library.onload();
  return { panel, window, calls, timers, requests, responses, library, login: (credential) => callback({ credential }) };
}

test("page load does not restore login or automatically reissue tokens", () => {
  const b = browser();
  assert.equal(b.requests.length, 0);
  assert.equal(b.calls.length, 0);
  assert.equal(b.timers.size, 0);
});

test("login exchanges Google ID token and registers only service JWT in Swagger", async () => {
  const b = browser();
  b.responses.push({ ok: true, json: async () => ({ accessToken: "service-jwt", expiresIn: 900 }) });
  await b.login("google-id-token");
  assert.equal(b.requests[0].url, "/dev/api/auth/login/google");
  assert.equal(b.requests[0].options.credentials, "same-origin");
  assert.equal(b.requests[0].options.body, '{"idToken":"google-id-token"}');
  assert.equal(b.requests[0].options.headers.Authorization, undefined);
  assert.deepEqual(b.calls.find((call) => call[0] === "authorize"), ["authorize", "bearerAuth", "service-jwt"]);
  assert.match(b.panel.children[2].textContent, /로그인 완료/);
  const timer = [...b.timers.values()][0];
  assert.equal(timer.delay, 900000);
  timer.handler();
  assert.match(b.panel.children[2].textContent, /만료/);
  assert.equal(b.timers.size, 0);
});

test("invalid Google token clears auth and displays server error", async () => {
  const b = browser();
  b.responses.push({ ok: false, status: 401, json: async () => ({ detail: "Invalid Google token" }) });
  await b.login("invalid-token");
  assert.equal(b.calls.some((call) => call[0] === "authorize"), false);
  assert.match(b.panel.children[2].textContent, /로그인 실패: Invalid Google token/);
  assert.equal(b.panel.children[1].disabled, false);
});

test("missing credentials and malformed login response do not authorize", async () => {
  const b = browser();
  await b.login(undefined);
  assert.equal(b.requests.length, 0);
  b.responses.push({ ok: true, json: async () => ({}) });
  await b.login("google-id-token");
  assert.equal(b.calls.some((call) => call[0] === "authorize"), false);
  assert.match(b.panel.children[2].textContent, /유효한 인증 정보/);
});

test("logout clears Swagger auth and expires server session using cookie", async () => {
  const b = browser();
  b.responses.push({ ok: true });
  await b.panel.children[1].events.click();
  assert.equal(b.requests[0].url, "/dev/api/logout");
  assert.equal(b.requests[0].options.credentials, "same-origin");
  assert.equal(b.calls[0][0], "clear");
  assert.match(b.panel.children[2].textContent, /로그아웃되었습니다/);
});

test("logout failure still clears local auth and warns server session may remain", async () => {
  const b = browser();
  b.responses.push(new Error("Network unavailable"));
  await b.panel.children[1].events.click();
  assert.equal(b.calls[0][0], "clear");
  assert.match(b.panel.children[2].textContent, /서버 로그아웃에 실패/);
});

test("Google library load error is visible", () => {
  const b = browser();
  b.library.onerror();
  assert.match(b.panel.children[2].textContent, /스크립트를 불러오지 못했습니다/);
});

test("overlapping login callbacks do not create duplicate server sessions", async () => {
  const b = browser();
  let resolve;
  b.responses.push({ ok: true, json: () => new Promise((done) => { resolve = done; }) });
  const first = b.login("google-id-token");
  await Promise.resolve();
  await b.login("google-id-token");
  assert.equal(b.requests.length, 1);
  resolve({ accessToken: "service-jwt", expiresIn: 900 });
  await first;
});
