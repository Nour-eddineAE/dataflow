<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('password'); section>
    <#if section = "header">
        ${msg("passwordTitle")}
    <#elseif section = "show-username">
        <h1 id="kc-page-title">${msg("passwordTitle")}</h1>
    <#elseif section = "form">
        <form id="kc-form-login" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
            <div class="df-group">
                <div class="df-label-row">
                    <label for="password" class="df-label">${msg("password")}</label>
                    <#if realm.resetPasswordAllowed>
                        <a tabindex="4" href="${url.loginResetCredentialsUrl}">${msg("doForgotPassword")}</a>
                    </#if>
                </div>
                <div class="df-input-group" dir="ltr">
                    <input tabindex="1" id="password" class="df-input" name="password" type="password" autocomplete="current-password" autofocus
                           aria-invalid="<#if messagesPerField.existsError('password')>true</#if>"/>
                    <button class="df-reveal" type="button" aria-label="${msg('showPassword')}" aria-controls="password" data-password-toggle
                            tabindex="1" data-icon-show="df-eye" data-icon-hide="df-eye-off"
                            data-label-show="${msg('showPassword')}" data-label-hide="${msg('hidePassword')}">
                        <i class="df-eye" aria-hidden="true"></i>
                    </button>
                </div>
                <span id="input-error" class="df-field-error" aria-live="polite"><#if messagesPerField.existsError('password')>${kcSanitize(messagesPerField.get('password'))?no_esc}</#if></span>
            </div>
            <button tabindex="2" class="df-button df-button-primary df-button-block" name="login" id="kc-login" type="submit">${msg("doLogIn")}</button>
        </form>
        <script type="module" src="${url.resourcesPath}/js/passwordVisibility.js"></script>
    </#if>
</@layout.registrationLayout>
