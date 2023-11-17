package com.checkcheck.ecoreading.domain.users.service;

import com.checkcheck.ecoreading.domain.books.entity.Books;

import com.checkcheck.ecoreading.domain.pointHistory.entity.PointHistory;
import com.checkcheck.ecoreading.domain.users.dto.UserKakaoRegisterRequestDTO;
import com.checkcheck.ecoreading.domain.users.dto.UserLoginRequestDTO;
import com.checkcheck.ecoreading.domain.users.dto.UserOAuth2CustomDTO;
import com.checkcheck.ecoreading.domain.users.dto.UserRegisterRequestDTO;
import com.checkcheck.ecoreading.domain.users.dto.UserResponseDTO.TokenInfo;
import com.checkcheck.ecoreading.domain.users.entity.Role;
import com.checkcheck.ecoreading.domain.users.entity.Users;
import com.checkcheck.ecoreading.domain.users.repository.UserRepository;
import com.checkcheck.ecoreading.security.jwt.JwtTokenProvider;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;

import java.util.List;

import java.util.Arrays;

import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@Service
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    private static final String AUTH_CODE_PREFIX = "AuthCode:";

    private static final String PASSWORD_CODE_PREFIX = "PassWordCode:";

    private final MailService mailService;

    private final RedisService redisService;

    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    private final JwtTokenProvider jwtTokenProvider;

    @Value("${spring.mail.auth-code-expiration-millis}")
    private long authCodeExpirationMillis;


    public Long save(UserRegisterRequestDTO dto) {
        Users user = Users.builder()
                .email(dto.getEmail())
                .birthDate(dto.getBirthdate())
                .nickName(dto.getNickname())
                .phone(dto.getPhone())
                .userName(dto.getUsername())
                .password(bCryptPasswordEncoder.encode(dto.getPassword()))
                .role(Role.ROLE_USER)
                .detailAddress(dto.getDetailAddress())
                .roadAddress(dto.getRoadAddress())
                .postcode(dto.getPostcode())
                .enabled(true)
                .emailVerified(false)
                .build();
        user = userRepository.save(user);
        return user.getUsersId();
    }
    public Long saveKakao(UserKakaoRegisterRequestDTO dto){
        Users user = Users.builder()
                .email(dto.getEmail())
                .birthDate(dto.getBirthdate())
                .nickName(dto.getNickname())
                .phone(dto.getPhone())
                .userName(dto.getUsername())
                .socialAuthId(dto.getSocialAuthId())
                .socialAuth("kakao")
                .password(null)
                .role(Role.ROLE_USER)
                .detailAddress(dto.getDetailAddress())
                .roadAddress(dto.getRoadAddress())
                .postcode(dto.getPostcode())
                .enabled(true)
                .emailVerified(true)
                .build();
        user = userRepository.save(user);
        return user.getUsersId();
    }

    @Transactional
    public void sendCodeToEmail(String toEmail) {

        this.checkDuplicatedEmail(toEmail);

        String title = "[eco-reading 이메일 인증 번호]";

        String authCode = this.createCode();

        String htmlContents = "<p>ECO-READING 인증 번호 입니다.<p>"
                + "<p> 인증 번호 : " + authCode + "<p>";

        mailService.sendEmail(toEmail, title, htmlContents);
        // 이메일 인증 요청 시 인증 번호 Redis에 저장 ( key = "Email" / value = AuthCode )
        redisService.setValues(AUTH_CODE_PREFIX+toEmail, authCode, Duration.ofMillis(this.authCodeExpirationMillis));
    }
    private void checkDuplicatedEmail(String email) {
        Optional<Users> member = userRepository.findByEmail(email);
        if (member.isPresent()) {
            log.debug("userservice exception occur email: {}", email);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 존재하는 회원입니다.");
        }
    }
    private String createCode()  {
        int lenth = 6;
        try {
            Random random = SecureRandom.getInstanceStrong();
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < lenth; i++) {
                builder.append(random.nextInt(10));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            //log.debug("MemberService.createCode() exception occur");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "알고리즘 생성 에러");
        }
    }
    public void verifiedCode(String email, String code) {
        this.checkDuplicatedEmail(email);
        String redisAuthCode = redisService.getValues(AUTH_CODE_PREFIX + email);

        if (redisAuthCode == null || !redisAuthCode.equals(code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "인증 코드가 틀렸습니다 다시 확인부탁드립니다.");
        }
    }

    public TokenInfo kakaoLogin(UserOAuth2CustomDTO oauthUser, HttpServletResponse response) {
        // 사용자의 소셜 고유 ID를 기반으로 데이터베이스에서 사용자 조회
        Long socialAuthId = oauthUser.getSocialId();
        Optional<Users> userOpt = userRepository.findBySocialAuthId(socialAuthId);
        Users user = userOpt.orElseThrow(() -> new UsernameNotFoundException("User not found with socialAuthId: " + socialAuthId));
        CustomUserDetails userDetails = new CustomUserDetails(
                user.getUsersId(),
                user.getEmail(),
                "", // 카카오 로그인에서는 비밀번호가 없으므로 빈 문자열 전달
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        // JWT 토큰 생성
        TokenInfo tokenInfo = jwtTokenProvider.generateToken(authentication);
        // 응답에 토큰 쿠키 추가
        addTokenCookiesToResponse(tokenInfo, response);
        log.info("Kakao user logged in: {}", user.getEmail());
        return tokenInfo;
    }

    public TokenInfo login(UserLoginRequestDTO loginDto, HttpServletResponse response) {
        //1. dto를 통해 시큐리티 인증 등록
        Authentication authentication = authenticateUser(loginDto);
        //2. 토큰 발급
        TokenInfo tokenInfo = jwtTokenProvider.generateToken(authentication);
        //3. 쿠키에 저장
        addTokenCookiesToResponse(tokenInfo, response);
        return tokenInfo;
    }

    private Authentication authenticateUser(UserLoginRequestDTO loginDto) {
        UsernamePasswordAuthenticationToken authenticationToken = loginDto.toAuthentication();
        try {
            return authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        } catch (Exception e) {
            throw new AuthenticationServiceException("이메일 또는 비밀번호가 틀렸습니다.");
        }
    }
    private void addTokenCookiesToResponse(TokenInfo tokenInfo, HttpServletResponse response) {
        Cookie accessTokenCookie = createCookie("accessToken", tokenInfo.getAccessToken(), 30 * 60 * 1000L);
        Cookie refreshTokenCookie = createCookie("refreshToken", tokenInfo.getRefreshToken(), tokenInfo.getRefreshTokenExpirationTime());
        response.addCookie(accessTokenCookie);
        response.addCookie(refreshTokenCookie);
    }
    private Cookie createCookie(String name, String value, Long maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(false);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge.intValue());
        return cookie;
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            deleteCookies(request, response, "accessToken", "refreshToken");
            if (authentication.getPrincipal() instanceof UserDetails) {
                UserDetails userDetails = (UserDetails) authentication.getPrincipal();
                String userEmail = userDetails.getUsername();
                redisService.deleteValues("RefreshToken:" + userEmail);
            } else if (authentication.getPrincipal() instanceof String) {
                String username = (String) authentication.getPrincipal();
                redisService.deleteValues("RefreshToken:" + username);
            } else {
                throw new IllegalStateException("Unexpected type of principal object");
            }
        }
    }
    private void deleteCookies(HttpServletRequest request, HttpServletResponse response, String... cookieNames) {
        Arrays.asList(cookieNames).forEach(cookieName -> {
            Cookie cookie = new Cookie(cookieName, null);
            cookie.setHttpOnly(false);
            cookie.setSecure(false);
            cookie.setPath("/"); // 쿠키 경로를 설정합니다. 일반적으로 루트("/")를 사용합니다.
            cookie.setMaxAge(0); // 쿠키를 즉시 만료시킵니다.
            response.addCookie(cookie);
        });
    }

    public String createPasswordResetToken(String email) {
        System.out.println("email = " + email);
        Users user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        if ("kakao".equals(user.getSocialAuth())) {
            throw new UnsupportedOperationException("카카오 계정은 비밀번호 재설정을 지원하지 않습니다.");
        }
        String token = UUID.randomUUID().toString();
        System.out.println("token = " + token);
        redisService.setValues(PASSWORD_CODE_PREFIX+token,email,Duration.ofMillis(this.authCodeExpirationMillis));
        return token;
    }
    public String sendMailPasswordReset(String email, String token){
        String resetUrl = "http://localhost:8099/user/reset-password?token=" + token;
        String htmlContent = "<p>비밀번호를 재설정하려면 아래 링크를 클릭하세요</p>" +
                "<a href='" + resetUrl + "'>비밀번호 재설정 링크</a>";
        mailService.sendEmail(email, "[비밀번호 재설정 링크]", htmlContent);
        return null;
    }

    public Optional<Users> findByUserNameAndPhone(String name, String phone){
        return userRepository.findByUserNameAndPhone(name,phone);
    }

    public List<Users> findAll(){
        return userRepository.findAll();
    }

    public Users findAllById(Long usersId){
       return userRepository.findAllByUsersId(usersId);
    }

    public List<Users> findAllByEnabled(boolean enabled){
        return userRepository.findByEnabled(enabled);
    }
    public Integer findTotalPointByUsersId(Long usersId) {
        return userRepository.findTotalPointByUsersId(usersId);
    }


    public boolean validatePasswordResetToken(String token) {
        // 레디스에서 토큰을 이용해 사용자 이메일을 조회
        String email = redisService.getValues(PASSWORD_CODE_PREFIX+token);
        System.out.println("email = " + email);
        if (email == null) {
            return false;
        }
        // 데이터베이스에서 사용자 조회
        Optional<Users> user = userRepository.findByEmail(email);
        return user.isPresent();
    }

    public Long getUserIdFromAccessTokenCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        for (Cookie cookie : cookies) {
            if ("accessToken".equals(cookie.getName())) {
                return jwtTokenProvider.getUserIdFromToken(cookie.getValue());
            }
        }

        return null;
    }

    public void changePassword(String token, String newPassword) {
        String email = redisService.getValues(PASSWORD_CODE_PREFIX + token);
        if (email == null) {
            throw new IllegalStateException("유효하지 않은 토큰입니다.");
        }
        // 이메일 주소로 사용자 검색
        Users user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다."));

        // 새 비밀번호를 암호화
        String encodedPassword = bCryptPasswordEncoder.encode(newPassword);

        user.changePassword(encodedPassword);
        userRepository.save(user);

        // 사용된 토큰을 Redis에서 제거
        redisService.deleteValues("PASSWORD_CODE_PREFIX" + token);
    }
}



