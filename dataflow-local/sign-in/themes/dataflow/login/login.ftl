<#import "template.ftl" as layout>
<#import "passkeys.ftl" as passkeys>
<#import "providers.ftl" as providers>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>
    <#if section = "header">
        ${msg("loginAccountTitle")}
    <#elseif section = "form">
        <@providers.buttons/>
        <#if realm.password>
            <form id="kc-form-login" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post" novalidate>
                <#if !usernameHidden??>
                    <div class="df-group">
                        <label for="username" class="df-label">${msg("email")}</label>
                        <input tabindex="1" id="username" class="df-input" name="username" value="${(login.username!'')}" type="email"
                               inputmode="email" placeholder="${msg("emailPlaceholder")}" autofocus
                               autocomplete="${(enableWebAuthnConditionalUI?has_content)?then('username webauthn', 'email')}"
                               aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>" dir="ltr"/>
                    </div>
                </#if>
                <div class="df-group">
                    <div class="df-label-row">
                        <label for="password" class="df-label">${msg("password")}</label>
                        <#if realm.resetPasswordAllowed>
                            <a tabindex="5" href="${url.loginResetCredentialsUrl}">${msg("doForgotPassword")}</a>
                        </#if>
                    </div>
                    <div class="df-input-group" dir="ltr">
                        <input tabindex="2" id="password" class="df-input" name="password" type="password" autocomplete="current-password"
                               aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>"/>
                        <button class="df-reveal" type="button" aria-label="${msg("showPassword")}" aria-controls="password" data-password-toggle
                                tabindex="2" data-icon-show="df-eye" data-icon-hide="df-eye-off"
                                data-label-show="${msg('showPassword')}" data-label-hide="${msg('hidePassword')}">
                            <i class="df-eye" aria-hidden="true"></i>
                        </button>
                    </div>
                    <span id="input-error" class="df-field-error" aria-live="polite"><#if messagesPerField.existsError('username','password')>${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}</#if></span>
                </div>
                <#if realm.rememberMe && !usernameHidden??>
                    <label class="df-check">
                        <input tabindex="3" id="rememberMe" name="rememberMe" type="checkbox"<#if login.rememberMe??> checked</#if>>
                        <span>${msg("rememberMe")}</span>
                    </label>
                </#if>
                <input type="hidden" id="id-hidden-input" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>
                <button tabindex="4" class="df-button df-button-primary df-button-block" name="login" id="kc-login" type="submit">${msg("doLogIn")}</button>
            </form>
        </#if>
        <@passkeys.conditionalUIData />
        <script type="module" src="${url.resourcesPath}/js/passwordVisibility.js"></script>
    <#elseif section = "info">
        <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
            <span>${msg("noAccount")} <a tabindex="6" href="${url.registrationUrl}">${msg("doRegister")}</a></span>
        </#if>
    </#if>
</@layout.registrationLayout>
