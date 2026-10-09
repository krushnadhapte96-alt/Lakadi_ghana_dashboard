package com.ghana.dashboard.security;

import com.ghana.dashboard.common.DomainException;
import com.ghana.dashboard.domain.Role;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RoleGuardAspect {

    @Around("@annotation(roleGuard)")
    public Object checkRole(ProceedingJoinPoint joinPoint, RoleGuard roleGuard) throws Throwable {
        String requiredRole = roleGuard.requiredRole();
        
        Role currentRole = CurrentUser.getRole();
        
        if (!currentRole.name().equals(requiredRole)) {
            throw new DomainException(403, "Access denied. Required role: " + requiredRole);
        }
        
        return joinPoint.proceed();
    }
}
