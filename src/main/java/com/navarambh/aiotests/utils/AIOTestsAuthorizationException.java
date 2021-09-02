package com.navarambh.aiotests.utils;

public class AIOTestsAuthorizationException extends AIOTestsException {

    AIOTestsAuthorizationException() {
        super("User is unauthorized.  Please check API token (cloud) or Jira Username+Password(server/DC).");
    }

}
