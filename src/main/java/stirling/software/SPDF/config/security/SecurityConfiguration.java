package stirling.software.SPDF.config.security;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml4AuthenticationProvider;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.web.authentication.OpenSaml4AuthenticationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import lombok.extern.slf4j.Slf4j;

import stirling.software.SPDF.config.security.oauth2.CustomOAuth2AuthenticationFailureHandler;
import stirling.software.SPDF.config.security.oauth2.CustomOAuth2AuthenticationSuccessHandler;
import stirling.software.SPDF.config.security.oauth2.CustomOAuth2UserService;
import stirling.software.SPDF.config.security.saml2.CustomSaml2AuthenticationFailureHandler;
import stirling.software.SPDF.config.security.saml2.CustomSaml2AuthenticationSuccessHandler;
import stirling.software.SPDF.config.security.saml2.CustomSaml2ResponseAuthenticationConverter;
import stirling.software.SPDF.config.security.session.SessionPersistentRegistry;
import stirling.software.SPDF.model.ApplicationProperties;
import stirling.software.SPDF.model.User;
import stirling.software.SPDF.repository.JPATokenRepositoryImpl;
import stirling.software.SPDF.repository.PersistentLoginRepository;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Slf4j
@DependsOn("runningProOrHigher")
public class SecurityConfiguration {

    private static final String[] PUBLIC_APP_ARTWORK = {
        "/octapdf-logo.png",
        "/favicon.png",
        "/favicon-16x16.png",
        "/favicon-32x32.png",
        "/favicon.ico",
        "/apple-touch-icon.png",
        "/android-chrome-192x192.png",
        "/android-chrome-512x512.png",
        "/site.webmanifest",
        "/manifest.json"
    };

    private final CustomUserDetailsService userDetailsService;
    private final UserService userService;
    private final boolean loginEnabledValue;
    private final boolean runningProOrHigher;

    private final ApplicationProperties applicationProperties;
    private final UserAuthenticationFilter userAuthenticationFilter;
    private final LoginAttemptService loginAttemptService;
    private final FirstLoginFilter firstLoginFilter;
    private final SessionPersistentRegistry sessionRegistry;
    private final PersistentLoginRepository persistentLoginRepository;
    private final GrantedAuthoritiesMapper oAuth2userAuthoritiesMapper;
    private final RelyingPartyRegistrationRepository saml2RelyingPartyRegistrations;
    private final OpenSaml4AuthenticationRequestResolver saml2AuthenticationRequestResolver;

    public SecurityConfiguration(
            PersistentLoginRepository persistentLoginRepository,
            CustomUserDetailsService userDetailsService,
            @Lazy UserService userService,
            @Qualifier("loginEnabled") boolean loginEnabledValue,
            @Qualifier("runningProOrHigher") boolean runningProOrHigher,
            ApplicationProperties applicationProperties,
            UserAuthenticationFilter userAuthenticationFilter,
            LoginAttemptService loginAttemptService,
            FirstLoginFilter firstLoginFilter,
            SessionPersistentRegistry sessionRegistry,
            @Autowired(required = false) GrantedAuthoritiesMapper oAuth2userAuthoritiesMapper,
            @Autowired(required = false)
                    RelyingPartyRegistrationRepository saml2RelyingPartyRegistrations,
            @Autowired(required = false)
                    OpenSaml4AuthenticationRequestResolver saml2AuthenticationRequestResolver) {
        this.userDetailsService = userDetailsService;
        this.userService = userService;
        this.loginEnabledValue = loginEnabledValue;
        this.runningProOrHigher = runningProOrHigher;
        this.applicationProperties = applicationProperties;
        this.userAuthenticationFilter = userAuthenticationFilter;
        this.loginAttemptService = loginAttemptService;
        this.firstLoginFilter = firstLoginFilter;
        this.sessionRegistry = sessionRegistry;
        this.persistentLoginRepository = persistentLoginRepository;
        this.oAuth2userAuthoritiesMapper = oAuth2userAuthoritiesMapper;
        this.saml2RelyingPartyRegistrations = saml2RelyingPartyRegistrations;
        this.saml2AuthenticationRequestResolver = saml2AuthenticationRequestResolver;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.headers(
                headers -> {
                    headers.contentTypeOptions(Customizer.withDefaults());
                    headers.xssProtection(Customizer.withDefaults());
                    headers.frameOptions(frame -> frame.deny());
                    headers.httpStrictTransportSecurity(
                            hsts ->
                                    hsts.requestMatcher(request -> true)
                                            .includeSubDomains(true)
                                            .maxAgeInSeconds(31536000));
                    headers.referrerPolicy(
                            referrer ->
                                    referrer.policy(
                                            ReferrerPolicyHeaderWriter.ReferrerPolicy
                                                    .STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                    headers.permissionsPolicy(
                            permissions ->
                                    permissions.policy(
                                            "camera=(), microphone=(), geolocation=(), payment=(), usb=()"));
                    headers.contentSecurityPolicy(
                            csp -> csp.policyDirectives(SecurityHeadersFilter.CSP_POLICY));
                });

        if (applicationProperties.getSecurity().getCsrfDisabled() || !loginEnabledValue) {
            http.csrf(csrf -> csrf.disable());
        }

        if (loginEnabledValue) {
            http.addFilterBefore(
                    userAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
            if (!applicationProperties.getSecurity().getCsrfDisabled()) {
                CookieCsrfTokenRepository cookieRepo =
                        CookieCsrfTokenRepository.withHttpOnlyFalse();
                CsrfTokenRequestAttributeHandler requestHandler =
                        new CsrfTokenRequestAttributeHandler();
                requestHandler.setCsrfRequestAttributeName(null);
                http.csrf(
                        csrf ->
                                csrf.ignoringRequestMatchers(
                                                request -> {
                                                    String apiKey = request.getHeader("X-API-KEY");
                                                    // If there's no API key, don't ignore CSRF
                                                    // (return false)
                                                    if (apiKey == null || apiKey.trim().isEmpty()) {
                                                        return false;
                                                    }
                                                    // Validate API key using existing UserService
                                                    try {
                                                        Optional<User> user =
                                                                userService.getUserByApiKey(apiKey);
                                                        // If API key is valid, ignore CSRF (return
                                                        // true)
                                                        // If API key is invalid, don't ignore CSRF
                                                        // (return false)
                                                        return user.isPresent();
                                                    } catch (Exception e) {
                                                        // If there's any error validating the API
                                                        // key, don't ignore CSRF
                                                        return false;
                                                    }
                                                })
                                        .csrfTokenRepository(cookieRepo)
                                        .csrfTokenRequestHandler(requestHandler));
            }
            http.addFilterBefore(rateLimitingFilter(), UsernamePasswordAuthenticationFilter.class);
            http.addFilterAfter(firstLoginFilter, UsernamePasswordAuthenticationFilter.class);
            http.sessionManagement(
                    sessionManagement ->
                            sessionManagement
                                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                                    .maximumSessions(10)
                                    .maxSessionsPreventsLogin(false)
                                    .sessionRegistry(sessionRegistry)
                                    .expiredUrl("/login?logout=true"));
            http.authenticationProvider(daoAuthenticationProvider());
            http.requestCache(requestCache -> requestCache.requestCache(new NullRequestCache()));
            http.logout(
                    logout ->
                            logout.logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                                    .logoutSuccessHandler(
                                            new CustomLogoutSuccessHandler(applicationProperties))
                                    .clearAuthentication(true)
                                    .invalidateHttpSession(true)
                                    .deleteCookies("JSESSIONID", "remember-me"));
            http.rememberMe(
                    rememberMeConfigurer -> // Use the configurator directly
                    rememberMeConfigurer
                                    .tokenRepository(persistentTokenRepository())
                                    .tokenValiditySeconds( // 14 days
                                            14 * 24 * 60 * 60)
                                    .userDetailsService( // Your existing UserDetailsService
                                            userDetailsService)
                                    .useSecureCookie( // Enable secure cookie
                                            true)
                                    .rememberMeParameter( // Form parameter name
                                            "remember-me")
                                    .rememberMeCookieName( // Cookie name
                                            "remember-me")
                                    .alwaysRemember(false));
            http.authorizeHttpRequests(
                    authz ->
                            authz.requestMatchers(HttpMethod.GET, PUBLIC_APP_ARTWORK)
                                    .permitAll()
                                    .requestMatchers(HttpMethod.HEAD, PUBLIC_APP_ARTWORK)
                                    .permitAll()
                                    .requestMatchers(
                                            req -> {
                                                String uri = req.getRequestURI();
                                                String contextPath = req.getContextPath();
                                                // Remove the context path from the URI
                                                String trimmedUri =
                                                        uri.startsWith(contextPath)
                                                                ? uri.substring(
                                                                        contextPath.length())
                                                                : uri;
                                                return trimmedUri.startsWith("/login")
                                                        || trimmedUri.startsWith("/oauth")
                                                        || trimmedUri.startsWith("/saml2")
                                                        || trimmedUri.endsWith(".svg")
                                                        || trimmedUri.startsWith("/register")
                                                        || trimmedUri.startsWith("/error")
                                                        || trimmedUri.startsWith("/images/")
                                                        || trimmedUri.startsWith("/public/")
                                                        || trimmedUri.startsWith("/css/")
                                                        || trimmedUri.startsWith("/fonts/")
                                                        || trimmedUri.startsWith("/js/")
                                                        || trimmedUri.startsWith("/actuator/health")
                                                        || trimmedUri.startsWith(
                                                                "/api/v1/info/status");
                                            })
                                    .permitAll()
                                    .anyRequest()
                                    .authenticated());
            // Handle User/Password Logins
            if (applicationProperties.getSecurity().isUserPass()) {
                http.formLogin(
                        formLogin ->
                                formLogin
                                        .loginPage("/login")
                                        .successHandler(
                                                new CustomAuthenticationSuccessHandler(
                                                        loginAttemptService, userService))
                                        .failureHandler(
                                                new CustomAuthenticationFailureHandler(
                                                        loginAttemptService, userService))
                                        .defaultSuccessUrl("/")
                                        .permitAll());
            }
            // Handle OAUTH2 Logins
            if (applicationProperties.getSecurity().isOauth2Active()) {
                http.oauth2Login(
                        oauth2 ->
                                oauth2.loginPage("/oauth2")
                                        .
                                        /*
                                        This Custom handler is used to check if the OAUTH2 user trying to log in, already exists in the database.
                                        If user exists, login proceeds as usual. If user does not exist, then it is auto-created but only if 'OAUTH2AutoCreateUser'
                                        is set as true, else login fails with an error message advising the same.
                                         */
                                        successHandler(
                                                new CustomOAuth2AuthenticationSuccessHandler(
                                                        loginAttemptService,
                                                        applicationProperties,
                                                        userService))
                                        .failureHandler(
                                                new CustomOAuth2AuthenticationFailureHandler())
                                        . // Add existing Authorities from the database
                                        userInfoEndpoint(
                                                userInfoEndpoint ->
                                                        userInfoEndpoint
                                                                .oidcUserService(
                                                                        new CustomOAuth2UserService(
                                                                                applicationProperties,
                                                                                userService,
                                                                                loginAttemptService))
                                                                .userAuthoritiesMapper(
                                                                        oAuth2userAuthoritiesMapper))
                                        .permitAll());
            }
            // Handle SAML
            if (applicationProperties.getSecurity().isSaml2Active() && runningProOrHigher) {
                // Configure the authentication provider
                OpenSaml4AuthenticationProvider authenticationProvider =
                        new OpenSaml4AuthenticationProvider();
                authenticationProvider.setResponseAuthenticationConverter(
                        new CustomSaml2ResponseAuthenticationConverter(userService));
                http.authenticationProvider(authenticationProvider)
                        .saml2Login(
                                saml2 -> {
                                    try {
                                        saml2.loginPage("/saml2")
                                                .relyingPartyRegistrationRepository(
                                                        saml2RelyingPartyRegistrations)
                                                .authenticationManager(
                                                        new ProviderManager(authenticationProvider))
                                                .successHandler(
                                                        new CustomSaml2AuthenticationSuccessHandler(
                                                                loginAttemptService,
                                                                applicationProperties,
                                                                userService))
                                                .failureHandler(
                                                        new CustomSaml2AuthenticationFailureHandler())
                                                .authenticationRequestResolver(
                                                        saml2AuthenticationRequestResolver);
                                    } catch (Exception e) {
                                        log.error("Error configuring SAML 2 login", e);
                                        throw new RuntimeException(e);
                                    }
                                });
            }
        } else {
            log.debug("Login is not enabled. Anonymous mode active.");
            http.addFilterBefore(
                    rateLimitingFilter(),
                    org.springframework.security.web.access.intercept.AuthorizationFilter.class);
            http.authorizeHttpRequests(
                    authz ->
                            authz.requestMatchers("/api/v1/user/**", "/register", "/account/**")
                                    .denyAll()
                                    .anyRequest()
                                    .permitAll());
        }
        return http.build();
    }

    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public IPRateLimitingFilter rateLimitingFilter() {
        int requestsPerMinute = 60;
        int heavyRequestsPerMinute = 10;
        List<String> trustedProxies = Collections.emptyList();
        if (applicationProperties != null
                && applicationProperties.getSecurity() != null
                && applicationProperties.getSecurity().getRateLimit() != null) {
            var rl = applicationProperties.getSecurity().getRateLimit();
            requestsPerMinute = rl.getRequestsPerMinute();
            heavyRequestsPerMinute = rl.getHeavyRequestsPerMinute();
            trustedProxies = rl.getTrustedProxies();
        }
        return new IPRateLimitingFilter(requestsPerMinute, heavyRequestsPerMinute, trustedProxies);
    }

    @Bean
    public FilterRegistrationBean<IPRateLimitingFilter> rateLimitingFilterRegistration(
            IPRateLimitingFilter filter) {
        FilterRegistrationBean<IPRateLimitingFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PersistentTokenRepository persistentTokenRepository() {
        return new JPATokenRepositoryImpl(persistentLoginRepository);
    }

    @Bean
    public boolean activeSecurity() {
        return true;
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
