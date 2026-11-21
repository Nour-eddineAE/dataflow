<#import "template.ftl" as layout>
<#import "passkeys.ftl" as passkeys>
<#import "providers.ftl" as providers>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username') displayInfo=(realm.password && realm.registrationAllowed && !registrationDisabled??); section>
    <#if section = "header">
        ${msg("loginAccountTitle")}
    <#elseif section = "form">
        <@providers.buttons/>
        <#if realm.password>
            <form id="kc-form-login" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post" novalidate>
                <div class="df-group">
                    <label for="username" class="df-label">${msg("email")}</label>
                    <input tabindex="1" id="username" class="df-input" name="username" value="${(login.username!'')}" type="email"
                           inputmode="email" placeholder="${msg("emailPlaceholder")}" autofocus
                           autocomplete="${(enableWebAuthnConditionalUI?has_content)?then('username webauthn', 'email')}"
                           aria-invalid="<#if messagesPerField.existsError('username')>true</#if>" dir="ltr"/>
                    <span id="input-error" class="df-field-error" aria-live="polite"><#if messagesPerField.existsError('username')>${kcSanitize(messagesPerField.get('username'))?no_esc}</#if></span>
                </div>
                <#if realm.rememberMe>
                    <label class="df-check">
                        <input tabindex="2" id="rememberMe" name="rememberMe" type="checkbox"<#if login.rememberMe??> checked</#if>>
                        <span>${msg("rememberMe")}</span>
                    </label>
                </#if>
                <button tabindex="3" class="df-button df-button-primary df-button-block" name="login" id="kc-login" type="submit">${msg("doContinue")}</button>
            </form>
        </#if>
        <@passkeys.conditionalUIData />
    <#elseif section = "info">
        <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
            <span>${msg("noAccount")} <a tabindex="6" href="${url.registrationUrl}">${msg("doRegister")}</a></span>
        </#if>
    </#if>
</@layout.registrationLayout>
