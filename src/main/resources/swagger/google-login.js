(() => {
  "use strict";

  window.addEventListener("load", () => {
    const panel = document.getElementById("swagger-google-login");
    const ui = window.ui;
    if (!panel || !ui) return;

    const apiBase = panel.dataset.contextPath;
    const googleButton = document.createElement("div");
    const logoutButton = document.createElement("button");
    const status = document.createElement("span");
    panel.style.cssText = "display:flex;align-items:center;gap:16px;flex-wrap:wrap;padding:16px;font-family:sans-serif";
    logoutButton.type = "button";
    logoutButton.textContent = "로그아웃";
    status.setAttribute("role", "status");
    status.textContent = "구글 로그인으로 Swagger 인증을 자동 등록할 수 있습니다.";
    panel.append(googleButton, logoutButton, status);
    document.getElementById("swagger-ui").before(panel);

    let busy = false;
    let expiryTimer;
    const clearAuth = () => {
      window.clearTimeout(expiryTimer);
      ui.authActions.logout(["bearerAuth"]);
    };
    const setBusy = (value) => {
      busy = value;
      logoutButton.disabled = value;
      googleButton.style.pointerEvents = value ? "none" : "auto";
    };
    const request = async (path, body) => {
      const response = await fetch(apiBase + path, {
        method: "POST",
        credentials: "same-origin",
        ...(body ? { headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : {})
      });
      if (!response.ok) {
        const problem = await response.json().catch(() => ({}));
        throw new Error(problem.detail || problem.title || `요청 실패 (${response.status})`);
      }
      return response;
    };

    const handleCredential = async ({ credential }) => {
      if (busy) return;
      setBusy(true);
      clearAuth();
      status.textContent = "로그인 중…";
      try {
        if (!credential) throw new Error("Google ID 토큰을 받지 못했습니다.");
        const response = await request("/api/auth/login/google", { idToken: credential });
        const result = await response.json();
        if (!result.accessToken || !(result.expiresIn > 0)) {
          throw new Error("로그인 응답에 유효한 인증 정보가 없습니다.");
        }
        ui.preauthorizeApiKey("bearerAuth", result.accessToken);
        status.textContent = "로그인 완료. API 테스트를 사용할 수 있습니다.";
        expiryTimer = window.setTimeout(() => {
          clearAuth();
          status.textContent = "인증이 만료되었습니다. 구글 로그인을 다시 해주세요.";
        }, result.expiresIn * 1000);
      } catch (error) {
        clearAuth();
        status.textContent = `로그인 실패: ${error.message}`;
      } finally {
        setBusy(false);
      }
    };

    logoutButton.addEventListener("click", async () => {
      if (busy) return;
      setBusy(true);
      clearAuth();
      try {
        await request("/api/logout");
        window.google?.accounts.id.disableAutoSelect();
        status.textContent = "로그아웃되었습니다.";
      } catch (error) {
        status.textContent = `Swagger 인증은 해제됐지만 서버 로그아웃에 실패했습니다: ${error.message}`;
      } finally {
        setBusy(false);
      }
    });

    const library = document.createElement("script");
    library.src = "https://accounts.google.com/gsi/client";
    library.async = true;
    library.onload = () => {
      try {
        window.google.accounts.id.initialize({
          client_id: panel.dataset.clientId,
          callback: handleCredential,
          ux_mode: "popup",
          auto_select: false
        });
        window.google.accounts.id.renderButton(googleButton, { theme: "outline", size: "large" });
      } catch (error) {
        status.textContent = `구글 로그인 초기화 실패: ${error.message}`;
      }
    };
    library.onerror = () => {
      status.textContent = "구글 로그인 스크립트를 불러오지 못했습니다. 네트워크 또는 차단 설정을 확인해주세요.";
    };
    document.head.append(library);
  }, { once: true });
})();
