/* ============================================================
   ilikepho — Trang đăng nhập Admin (JS)
   - Toggle hiện/ẩn mật khẩu
   - Loading spinner cho nút submit (ngăn submit trùng)
   - Tự động focus ô đầu tiên khi trang tải (desktop)
   ============================================================ */
(function () {
  "use strict";

  var passwordToggle = document.querySelector("[data-toggle-password]");
  var passwordInput = document.getElementById("password");
  var form = document.querySelector("[data-login-form]");
  var submitBtn = form ? form.querySelector('button[type="submit"]') : null;

  /* Toggle mật khẩu */
  if (passwordToggle && passwordInput) {
    passwordToggle.addEventListener("click", function () {
      var show = passwordInput.type === "password";
      passwordInput.type = show ? "text" : "password";
      passwordToggle.setAttribute("aria-pressed", show ? "true" : "false");
      passwordToggle.setAttribute(
        "aria-label",
        show ? "Ẩn mật khẩu" : "Hiện mật khẩu"
      );
      passwordToggle.querySelector(".icon-eye").style.display = show ? "none" : "block";
      passwordToggle.querySelector(".icon-eye-off").style.display = show ? "block" : "none";
      passwordInput.focus();
    });
  }

  /* Loading trên submit */
  if (form && submitBtn) {
    form.addEventListener("submit", function () {
      submitBtn.setAttribute("aria-busy", "true");
      submitBtn.classList.add("is-loading");
      submitBtn.disabled = true;

      var spinner = document.createElement("span");
      spinner.className = "spinner";
      spinner.setAttribute("aria-hidden", "true");
      submitBtn.prepend(spinner);
    });
  }

  /* Focus ô đầu tiên (chỉ trên màn hình rộng, nơi form nằm phải) */
  if (window.innerWidth >= 900 && passwordInput) {
    var userInput = document.getElementById("username");
    if (userInput && !userInput.value) {
      userInput.focus();
    } else if (passwordInput && !passwordInput.value) {
      passwordInput.focus();
    }
  }
})();
