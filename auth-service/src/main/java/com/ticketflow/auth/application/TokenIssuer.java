package com.ticketflow.auth.application;

import com.ticketflow.auth.domain.User;

/** Issues access tokens for an authenticated user. Hides which token technology is used. */
public interface TokenIssuer {

	IssuedToken issue(User user);
}
