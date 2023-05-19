/*
 * Copyright (c) 2023 by MILOSZ GILGA <http://miloszgilga.pl>
 *
 * File name: AuthService.java
 * Last modified: 17/05/2023, 16:05
 * Project name: air-hub-master-server
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 *
 *     <http://www.apache.org/license/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the license.
 */

package pl.miloszgilga.network.auth;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;

import org.springframework.stereotype.Service;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.*;
import java.time.Instant;
import java.time.ZonedDateTime;

import org.jmpsl.security.user.AuthUser;
import org.jmpsl.security.OtaTokenService;
import org.jmpsl.security.jwt.JwtService;
import org.jmpsl.security.jwt.JwtValidPayload;
import org.jmpsl.security.jwt.JwtValidationType;
import org.jmpsl.core.i18n.LocaleMessageService;
import org.jmpsl.communication.mail.MailRequestDto;
import org.jmpsl.communication.mail.JmpslMailService;

import pl.miloszgilga.utils.Utilities;
import pl.miloszgilga.utils.MailTemplate;
import pl.miloszgilga.config.ApiProperties;
import pl.miloszgilga.i18n.AppLocaleSet;
import pl.miloszgilga.dto.SimpleMessageResDto;
import pl.miloszgilga.security.JwtClaim;
import pl.miloszgilga.security.JwtIssuer;
import pl.miloszgilga.security.GrantedUserRole;

import pl.miloszgilga.network.auth.reqdto.LoginReqDto;
import pl.miloszgilga.network.auth.reqdto.RefreshReqDto;
import pl.miloszgilga.network.auth.reqdto.RegisterReqDto;
import pl.miloszgilga.network.auth.reqdto.ActivateAccountReqDto;
import pl.miloszgilga.network.auth.resdto.JwtAuthenticationResDto;

import pl.miloszgilga.domain.user.UserEntity;
import pl.miloszgilga.domain.user.IUserRepository;
import pl.miloszgilga.domain.ota_token.OtaTokenType;
import pl.miloszgilga.domain.ota_token.OtaTokenEntity;
import pl.miloszgilga.domain.ota_token.IOtaTokenRepository;
import pl.miloszgilga.domain.blacklist_jwt.BlacklistJwtEntity;
import pl.miloszgilga.domain.refresh_token.RefreshTokenEntity;
import pl.miloszgilga.domain.refresh_token.IRefreshTokenRepository;

import static pl.miloszgilga.exception.AuthException.UserNotFoundException;
import static pl.miloszgilga.exception.AuthException.IncorrectJwtException;
import static pl.miloszgilga.exception.AuthException.OtaTokenNotFoundException;
import static pl.miloszgilga.exception.AuthException.RefreshTokenNotFoundException;
import static pl.miloszgilga.exception.AuthException.JwtIsNotRelatedWithRefrehTokenException;
import static pl.miloszgilga.exception.AuthException.UserAccountHasBeenAlreadyActivatedException;

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements IAuthService {

    private final JwtIssuer jwtIssuer;
    private final JwtService jwtService;
    private final ApiProperties properties;
    private final OtaTokenService otaTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JmpslMailService jmpslMailService;
    private final LocaleMessageService messageService;
    private final AuthenticationManager authenticationManager;

    private final IUserRepository userRepository;
    private final IOtaTokenRepository otaTokenRepository;
    private final IRefreshTokenRepository refreshTokenRepository;

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public JwtAuthenticationResDto login(LoginReqDto reqDto) {
        final var authenticationToken = new UsernamePasswordAuthenticationToken(reqDto.getLoginOrEmail(), reqDto.getPassword());
        final Authentication authentication = authenticationManager.authenticate(authenticationToken);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        final var principal = (AuthUser) authentication.getPrincipal();

        final UserEntity userEntity = userRepository.findUserByLoginOrEmail(principal.getUsername())
            .orElseThrow(UserNotFoundException::new);

        final String token = jwtIssuer.generateTokenForUser(userEntity);
        final RefreshTokenEntity refreshToken = refreshTokenRepository.findRefreshTokenByUserLogin(userEntity.getLogin())
            .orElseGet(() -> {
                final var generatedRefreshToken = jwtService.generateRefreshToken();
                final RefreshTokenEntity refreshTokenEntity = RefreshTokenEntity.builder()
                    .token(generatedRefreshToken.token())
                    .expiredAt(generatedRefreshToken.expiredDate())
                    .build();
                refreshTokenRepository.save(refreshTokenEntity);
                log.info("Successfully created refresh token for '{}' account", principal.getUsername());
                return refreshTokenEntity;
            });
        if (refreshToken.getExpiredAt().isBefore(ZonedDateTime.now())) {
            final var generatedRefreshToken = jwtService.generateRefreshToken();
            refreshToken.setToken(generatedRefreshToken.token());
            refreshToken.setExpiredAt(generatedRefreshToken.expiredDate());
            refreshTokenRepository.save(refreshToken);
            log.info("Successfully re-validated expired refresh token for '{}' account", principal.getUsername());
        }
        log.info("Successfully login on '{}' account", principal.getUsername());
        return JwtAuthenticationResDto.builder()
            .jwtToken(token)
            .refreshToken(refreshToken.getToken())
            .build();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public SimpleMessageResDto register(RegisterReqDto reqDto) {
        final var generatedRefreshToken = jwtService.generateRefreshToken();
        final RefreshTokenEntity refreshTokenEntity = RefreshTokenEntity.builder()
            .token(generatedRefreshToken.token())
            .expiredAt(generatedRefreshToken.expiredDate())
            .build();

        String token;
        final Date expiredAt = DateUtils.addHours(Date.from(Instant.now()), properties.getOtaExpiredRegisterHours());
        do {
            token = otaTokenService.generateToken();
        } while (otaTokenRepository.checkIfTokenAlreadyExist(token));

        final OtaTokenEntity otaTokenEntity = OtaTokenEntity.builder()
            .token(token)
            .type(OtaTokenType.ACTIVATE_ACCOUNT)
            .expiredAt(ZonedDateTime.ofInstant(expiredAt.toInstant(), ZonedDateTime.now().getZone()))
            .build();

        final UserEntity user = UserEntity.builder()
            .firstName(StringUtils.capitalize(reqDto.getFirstName()))
            .lastName(StringUtils.capitalize(reqDto.getLastName()))
            .login(reqDto.getLogin())
            .emailAddress(reqDto.getEmailAddress())
            .password(passwordEncoder.encode(reqDto.getPassword()))
            .role(GrantedUserRole.STANDARD)
            .build();

        final MailRequestDto requestDto  = MailRequestDto.builder()
            .sendTo(Set.of(user.getEmailAddress()))
            .sendFrom(properties.getMailResponder())
            .replyAddress(properties.getReplyResponder())
            .messageSubject(messageService.getMessage(AppLocaleSet.REGISTER_TITLE_MAIL, Map.of(
                "appName", properties.getAppName(),
                "userLogin", user.getLogin()
            )))
            .appName(properties.getAppName())
            .locale(LocaleContextHolder.getLocale())
            .build();

        final Map<String, String> parameters = new HashMap<>();
        parameters.put("fullName", Utilities.parseFullName(user));
        parameters.put("token", token);
        parameters.put("expirationHours", String.valueOf(properties.getOtaExpiredRegisterHours()));

        jmpslMailService.sendEmail(requestDto, parameters, MailTemplate.REGISTER);

        user.persistRefreshToken(refreshTokenEntity);
        user.persistOtaToken(otaTokenEntity);
        userRepository.save(user);

        log.info("Successfully registered new user. User data: {}", user);
        return new SimpleMessageResDto(messageService.getMessage(AppLocaleSet.REGISTERED_RES,
            Map.of("email", reqDto.getEmailAddress())));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public SimpleMessageResDto activateAccount(ActivateAccountReqDto reqDto) {
        // code here
        return new SimpleMessageResDto(messageService.getMessage(AppLocaleSet.SUCCESSFULL_ACTIVATED_ACCOUNT_RES));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public JwtAuthenticationResDto refresh(RefreshReqDto reqDto) {
        final JwtValidPayload validationResult = jwtService.isValid(reqDto.getExpiredJwt());
        if (!validationResult.isValid() && !validationResult.checkType(JwtValidationType.EXPIRED)) {
            throw new IncorrectJwtException(reqDto.getExpiredJwt());
        }
        final RefreshTokenEntity refreshToken = refreshTokenRepository.findRefreshTokenByToken(reqDto.getRefreshToken())
            .orElseThrow(() -> new RefreshTokenNotFoundException(reqDto.getRefreshToken()));

        final Claims claims = jwtService.extractClaims(reqDto.getExpiredJwt())
            .orElseThrow(() -> new IncorrectJwtException(reqDto.getExpiredJwt()));

        final String login = claims.get(JwtClaim.LOGIN.getName(), String.class);
        final UserEntity user = refreshToken.getUser();
        if (!login.equals(user.getLogin())) throw new JwtIsNotRelatedWithRefrehTokenException(reqDto);

        log.info("JWT was successfully refreshed for user. User data: {}", user);
        return JwtAuthenticationResDto.builder()
            .jwtToken(jwtIssuer.generateTokenForUser(user))
            .refreshToken(refreshToken.getToken())
            .build();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public SimpleMessageResDto logout(HttpServletRequest req, AuthUser authUser) {
        final String extractedToken = jwtService.extractToken(req);
        final UserEntity user = userRepository.findUserByLoginOrEmail(authUser.getUsername())
            .orElseThrow(UserNotFoundException::new);

        final Claims claims = jwtService.extractClaims(extractedToken)
            .orElseThrow(() -> new IncorrectJwtException(extractedToken));

        final BlacklistJwtEntity blacklistJwt = BlacklistJwtEntity.builder()
            .jwtToken(extractedToken)
            .expiredAt(ZonedDateTime.ofInstant(claims.getExpiration().toInstant(), ZonedDateTime.now().getZone()))
            .build();

        user.persistBlacklistJwt(blacklistJwt);
        userRepository.save(user);
        SecurityContextHolder.clearContext();

        log.info("Successfully logout from '{}' account", user.getLogin());
        return new SimpleMessageResDto(messageService.getMessage(AppLocaleSet.LOGOUT_RES));
    }
}
