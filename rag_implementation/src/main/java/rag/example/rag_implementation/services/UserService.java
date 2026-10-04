package rag.example.rag_implementation.services;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import rag.example.rag_implementation.model.User;
import rag.example.rag_implementation.repository.UserRepository;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> getUsers() {
        return userRepository.getAllUsers();
    }

    /**
     * Resolves the authenticated caller for the current request. The JWT
     * filter puts the user's email (as the Spring Security username) into
     * the SecurityContext, so this just looks that user back up - every
     * endpoint that reaches here is already behind SecurityConfig's
     * "anyRequest().authenticated()", so an authentication is guaranteed
     * to be present.
     */
    public User getCurrentUser() {

        String email = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        return userRepository.findByEmail(email);
    }

}