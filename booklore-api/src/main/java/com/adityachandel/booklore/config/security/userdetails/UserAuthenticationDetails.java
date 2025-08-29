package com.adityachandel.booklore.config.security.userdetails;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

@Getter
public class UserAuthenticationDetails extends WebAuthenticationDetails {

    private final Long userId;

    public UserAuthenticationDetails(HttpServletRequest request, Long userId) {
        super(request);
        this.userId = userId;
    }
}
