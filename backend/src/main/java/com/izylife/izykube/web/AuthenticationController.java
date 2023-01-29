package com.izylife.izykube.web;

import com.izylife.izykube.services.JwtUserDetailsService;
import com.izylife.izykube.utils.JwtTokenUtil;
import com.izylife.openapi.api.AuthenticationApi;
import com.izylife.openapi.model.JwtRequest;
import com.izylife.openapi.model.JwtResponse;
import com.izylife.openapi.model.JwtResponseAuthorities;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@RestController
public class AuthenticationController implements AuthenticationApi {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private JwtUserDetailsService userDetailsService;


    private void authenticate(String username, String password) throws Exception {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        } catch (DisabledException e) {
            throw new Exception("USER_DISABLED", e);
        } catch (BadCredentialsException e) {
            throw new Exception("INVALID_CREDENTIALS", e);
        }
    }

    private List<JwtResponseAuthorities> convertAuthorities(Collection<? extends GrantedAuthority> authorities){
        List<JwtResponseAuthorities> jwtResponseAuthorities = new ArrayList<>();
        for(GrantedAuthority authority : authorities){
            JwtResponseAuthorities responseAuthorities = new JwtResponseAuthorities();
            responseAuthorities.setAuthority(authority.getAuthority());
            jwtResponseAuthorities.add(responseAuthorities);
        }
        return jwtResponseAuthorities;
    }


    @Override
    public ResponseEntity<JwtResponse> createAuthenticationToken(JwtRequest authenticationRequest) {
        JwtResponse response = null;
        try {
            authenticate(authenticationRequest.getUsername(), authenticationRequest.getPassword());
            final UserDetails userDetails = userDetailsService.loadUserByUsername(authenticationRequest.getUsername());
            final String token = jwtTokenUtil.generateToken(userDetails);
            response = new JwtResponse();
            response.setToken(token);
            response.setUsername( userDetails.getUsername());
            response.setAuthorities(convertAuthorities(userDetails.getAuthorities()));
        } catch (BadCredentialsException e) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<JwtResponse>(response, HttpStatus.OK);
    }

}
