package com.holymeowlabs.catnipkiosk.policy

import com.google.common.truth.Truth.assertWithMessage
import com.holymeowlabs.catnipkiosk.policy.NavDecision.ALLOW
import com.holymeowlabs.catnipkiosk.policy.NavDecision.BLOCK
import com.holymeowlabs.catnipkiosk.settings.KioskSettings
import com.holymeowlabs.catnipkiosk.settings.NavMode
import org.junit.Test

class NavigationPolicyTest {

    private val domain = KioskSettings(startUrl = "https://example.com/")

    private fun check(settings: KioskSettings, vararg cases: Pair<String, NavDecision>) {
        for ((url, expected) in cases) {
            assertWithMessage("decide(\"$url\") with start ${settings.startUrl}")
                .that(NavigationPolicy.decide(url, settings))
                .isEqualTo(expected)
        }
    }

    @Test
    fun nonWebSchemesAreBlocked() = check(
        domain,
        "intent://scan/#Intent;scheme=zxing;end" to BLOCK,
        "market://details?id=com.example" to BLOCK,
        "mailto:someone@example.com" to BLOCK,
        "tel:+15555550100" to BLOCK,
        "sms:+15555550100" to BLOCK,
        "file:///sdcard/secret.txt" to BLOCK,
        "content://com.example.provider/x" to BLOCK,
        "data:text/html,<h1>hi</h1>" to BLOCK,
        "javascript:alert(1)" to BLOCK,
        "about:config" to BLOCK,
    )

    @Test
    fun aboutBlankIsAllowed() = check(domain, "about:blank" to ALLOW)

    @Test
    fun sameHostAnyPathAndEitherWebSchemeIsAllowed() = check(
        domain,
        "https://example.com/x/y?z=1" to ALLOW,
        "http://example.com/" to ALLOW,
    )

    @Test
    fun hostMatchingIgnoresCaseAndTrailingDot() = check(domain, "https://EXAMPLE.com./a" to ALLOW)

    @Test
    fun subdomainsFollowTheToggle() {
        check(domain, "https://app.example.com/" to ALLOW)
        check(domain.copy(includeSubdomains = false), "https://app.example.com/" to BLOCK)
    }

    @Test
    fun lookalikeDomainsAreBlocked() = check(
        domain,
        "https://evilexample.com/" to BLOCK,
        "https://example.com.evil.example/" to BLOCK,
    )

    @Test
    fun extraDomainsAllowThemselvesAndTheirSubdomainsOnly() = check(
        domain.copy(extraDomains = listOf("login.example.org")),
        "https://login.example.org/auth" to ALLOW,
        "https://a.login.example.org/" to ALLOW,
        "https://example.org/" to BLOCK,
    )

    @Test
    fun extraDomainsStillApplyWhenSubdomainsAreOff() = check(
        domain.copy(includeSubdomains = false, extraDomains = listOf("login.example.org")),
        "https://a.login.example.org/" to ALLOW,
    )

    @Test
    fun internationalisedHostsCompareInPunycode() = check(
        // WebView hands us canonical (punycode) URLs; start URLs are normalised at input time.
        KioskSettings(startUrl = "https://xn--bcher-kva.example/"),
        "https://xn--bcher-kva.example/p" to ALLOW,
        "https://XN--BCHER-KVA.example/p" to ALLOW,
    )

    // Parser-differential guard: anything java.net.URI cannot resolve to a host
    // fails closed instead of being re-parsed by hand.
    @Test
    fun urlsWithoutAParsableHostFailClosed() = check(
        domain,
        "https://bücher.example.com/" to BLOCK,
        "https://evil.example%2F.example.com/" to BLOCK,
        "https://exa_mple.com/" to BLOCK,
        "https://example.com＠evil.example/" to BLOCK,
        "https://evil.example\\@example.com/" to BLOCK,
    )

    @Test
    fun pageOnlyAllowsSamePathWithAnyQueryOrFragment() = check(
        KioskSettings(startUrl = "https://example.com/lobby?x=1", navMode = NavMode.PAGE_ONLY),
        "https://example.com/lobby?x=2#top" to ALLOW,
        "https://example.com/lobby" to ALLOW,
        "https://example.com/lobby/" to BLOCK,
        "https://example.com/other" to BLOCK,
        "https://app.example.com/lobby" to BLOCK,
    )

    @Test
    fun pageOnlyTreatsEmptyPathAsRoot() = check(
        KioskSettings(startUrl = "https://example.com", navMode = NavMode.PAGE_ONLY),
        "https://example.com/" to ALLOW,
    )

    @Test
    fun ipStartHostsMatchExactlyAndIgnorePorts() = check(
        KioskSettings(startUrl = "http://192.168.1.20:8123/"),
        "http://192.168.1.20:8123/history" to ALLOW,
        "http://192.168.1.20:9000/" to ALLOW,
        "http://10.192.168.1.20/" to BLOCK,
        "http://1.20/" to BLOCK,
    )

    @Test
    fun localhostAndLoopbackIpAreDifferentHosts() = check(
        KioskSettings(startUrl = "http://localhost:8080/"),
        "http://localhost:9999/x" to ALLOW,
        "http://127.0.0.1:8080/" to BLOCK,
    )

    @Test
    fun unparsableUrlsAreBlocked() = check(
        domain,
        "http://exa mple.com/" to BLOCK,
        "" to BLOCK,
        "https://" to BLOCK,
    )

    @Test
    fun unparsableStartUrlBlocksEverything() = check(
        KioskSettings(startUrl = "not a url"),
        "https://example.com/" to BLOCK,
    )
}
