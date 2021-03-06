/**
 * Ambition Inc.
 * Copyright (c) 2006-2015 All Rights Reserved.
 */
package com.nuls.io.security;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.codec.Utf8;
import org.springframework.security.web.authentication.rememberme.AbstractRememberMeServices;
import org.springframework.security.web.authentication.rememberme.InvalidCookieException;
import org.springframework.util.StringUtils;

import com.nuls.io.model.entity.LogonInfo;
import com.nuls.io.service.SpringSecurityService;
import com.nuls.io.utils.NetworkUtil;

/**
 * 自定义cookie验证service，在Spring原有基础之上加上IP验证
 * @author cyh
 * @version $Id: MyTokenBasedRememberMeServices.java, v 0.1 2015年8月7日 上午10:10:27 cyh Exp $
 */
public class MyTokenBasedRememberMeServices extends AbstractRememberMeServices {

    @Autowired
    private SpringSecurityService springSecurityService;

    public MyTokenBasedRememberMeServices(String key, UserDetailsService userDetailsService) {
        super(key, userDetailsService);
    }

    /**
     * Getter method for property <tt>springSecurityService</tt>.
     * 
     * @return property value of springSecurityService
     */
    public SpringSecurityService getSpringSecurityService() {
        return springSecurityService;
    }

    /**
     * Setter method for property <tt>springSecurityService</tt>.
     * 
     * @param springSecurityService value to be assigned to property springSecurityService
     */
    public void setSpringSecurityService(SpringSecurityService springSecurityService) {
        this.springSecurityService = springSecurityService;
    }

    //~ Methods ========================================================================================================
    /**
     * cookie自动登录
     * @param cookieTokens
     * @param request
     * @param response
     * @return
     * @see org.springframework.security.web.authentication.rememberme.AbstractRememberMeServices#processAutoLoginCookie(java.lang.String[], javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    protected UserDetails processAutoLoginCookie(String[] cookieTokens, HttpServletRequest request,
                                                 HttpServletResponse response) {

        if (cookieTokens.length != 3) {
            throw new InvalidCookieException("Cookie token did not contain 3"
                                             + " tokens, but contained '"
                                             + Arrays.asList(cookieTokens) + "'");
        }

        long tokenExpiryTime;

        try {
            tokenExpiryTime = new Long(cookieTokens[1]).longValue();
        } catch (NumberFormatException nfe) {
            throw new InvalidCookieException(
                "Cookie token[1] did not contain a valid number (contained '" + cookieTokens[1]
                        + "')");
        }

        if (isTokenExpired(tokenExpiryTime)) {
            throw new InvalidCookieException("Cookie token[1] has expired (expired on '"
                                             + new Date(tokenExpiryTime) + "'; current time is '"
                                             + new Date() + "')");
        }
        String ipaddress = "";
        try {
            ipaddress = NetworkUtil.getIpAddress(request);
        } catch (IOException e) {
            logger.error("Error{}", e);
        }

        // Check the user exists.
        // Defer lookup until after expiry time checked, to possibly avoid expensive database call.

        UserDetails userDetails = getUserDetailsService().loadUserByUsername(cookieTokens[0]);

        // Check signature of token matches remaining details.
        // Must do this after user lookup, as we need the DAO-derived password.
        // If efficiency was a major issue, just add in a UserCache implementation,
        // but recall that this method is usually only called once per HttpSession - if the token is valid,
        // it will cause SecurityContextHolder population, whilst if invalid, will cause the cookie to be cancelled.
        String expectedTokenSignature = makeTokenSignature(tokenExpiryTime,
            userDetails.getUsername(), userDetails.getPassword(), ipaddress);

        if (!equals(expectedTokenSignature, cookieTokens[2])) {
            throw new InvalidCookieException("Cookie token[2] contained signature '"
                                             + cookieTokens[2] + "' but expected '"
                                             + expectedTokenSignature + "'");
        }
        LogonInfo users = springSecurityService.getByNameWithNoAuth(cookieTokens[0]);
        springSecurityService.initData(users);
        return userDetails;
    }

    /**
     * Calculates the digital signature to be put in the cookie. Default value is
     * MD5 ("username:tokenExpiryTime:password:key:ipaddress")
     */
    protected String makeTokenSignature(long tokenExpiryTime, String username, String password,
                                        String ipaddress) {
        String data = username + ":" + tokenExpiryTime + ":" + password + ":" + getKey() + ":"
                      + ipaddress;
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("No MD5 algorithm available!");
        }

        return new String(Hex.encode(digest.digest(data.getBytes())));
    }

    protected boolean isTokenExpired(long tokenExpiryTime) {
        return tokenExpiryTime < System.currentTimeMillis();
    }

    /**
     * 手动登录，存储cookie
     * @param request
     * @param response
     * @param successfulAuthentication
     * @see org.springframework.security.web.authentication.rememberme.AbstractRememberMeServices#onLoginSuccess(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, org.springframework.security.core.Authentication)
     */
    @Override
    public void onLoginSuccess(HttpServletRequest request, HttpServletResponse response,
                               Authentication successfulAuthentication) {

        String username = retrieveUserName(successfulAuthentication);
        String password = retrievePassword(successfulAuthentication);

        // If unable to find a username and password, just abort as TokenBasedRememberMeServices is
        // unable to construct a valid token in this case.
        if (!StringUtils.hasLength(username)) {
            logger.debug("Unable to retrieve username");
            return;
        }

        if (!StringUtils.hasLength(password)) {
            UserDetails user = getUserDetailsService().loadUserByUsername(username);
            password = user.getPassword();

            if (!StringUtils.hasLength(password)) {
                logger.debug("Unable to obtain password for user: " + username);
                return;
            }
        }

        int tokenLifetime = calculateLoginLifetime(request, successfulAuthentication);
        long expiryTime = System.currentTimeMillis();
        // SEC-949
        expiryTime += 1000L * (tokenLifetime < 0 ? TWO_WEEKS_S : tokenLifetime);
        String ipaddress = "";
        try {
            ipaddress = NetworkUtil.getIpAddress(request);
        } catch (IOException e) {
            logger.error("Error{}", e);
        }
        String signatureValue = makeTokenSignature(expiryTime, username, password, ipaddress);

        setCookie(new String[] { username, Long.toString(expiryTime), signatureValue },
            tokenLifetime, request, response);

        if (logger.isDebugEnabled()) {
            logger.debug("Added remember-me cookie for user '" + username + "', expiry: '"
                         + new Date(expiryTime) + "'");
        }
    }

    /**
     * Calculates the validity period in seconds for a newly generated remember-me login.
     * After this period (from the current time) the remember-me login will be considered expired.
     * This method allows customization based on request parameters supplied with the login or information in
     * the <tt>Authentication</tt> object. The default value is just the token validity period property,
     * <tt>tokenValiditySeconds</tt>.
     * <p>
     * The returned value will be used to work out the expiry time of the token and will also be
     * used to set the <tt>maxAge</tt> property of the cookie.
     *
     * See SEC-485.
     *
     * @param request the request passed to onLoginSuccess
     * @param authentication the successful authentication object.
     * @return the lifetime in seconds.
     */
    protected int calculateLoginLifetime(HttpServletRequest request, Authentication authentication) {
        return getTokenValiditySeconds();
    }

    protected String retrieveUserName(Authentication authentication) {
        if (isInstanceOfUserDetails(authentication)) {
            return ((UserDetails) authentication.getPrincipal()).getUsername();
        } else {
            return authentication.getPrincipal().toString();
        }
    }

    protected String retrievePassword(Authentication authentication) {
        if (isInstanceOfUserDetails(authentication)) {
            return ((UserDetails) authentication.getPrincipal()).getPassword();
        } else {
            if (authentication.getCredentials() == null) {
                return null;
            }
            return authentication.getCredentials().toString();
        }
    }

    private boolean isInstanceOfUserDetails(Authentication authentication) {
        return authentication.getPrincipal() instanceof UserDetails;
    }

    /**
     * Constant time comparison to prevent against timing attacks.
     */
    private static boolean equals(String expected, String actual) {
        byte[] expectedBytes = bytesUtf8(expected);
        byte[] actualBytes = bytesUtf8(actual);
        if (expectedBytes.length != actualBytes.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < expectedBytes.length; i++) {
            result |= expectedBytes[i] ^ actualBytes[i];
        }
        return result == 0;
    }

    private static byte[] bytesUtf8(String s) {
        if (s == null) {
            return null;
        }
        return Utf8.encode(s);
    }

}
