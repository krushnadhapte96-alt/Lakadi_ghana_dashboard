package com.ghana.dashboard.security;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RoleGuard {
    String requiredRole() default "OWNER";
}
