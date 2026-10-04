package rag.example.rag_implementation.services;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import rag.example.rag_implementation.dto.LoginRequestDTO;
import rag.example.rag_implementation.dto.LoginResponseDTO;
import rag.example.rag_implementation.dto.RegisterRequestDTO;
import rag.example.rag_implementation.exception.InvalidCredentialsException;
import rag.example.rag_implementation.exception.UserAlreadyExistsException;
import rag.example.rag_implementation.model.User;
import rag.example.rag_implementation.repository.UserRepository;
import rag.example.rag_implementation.security.JwtService;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    public void register(RegisterRequestDTO request) {

        User existingUser = userRepository.findByEmail(request.getEmail());

        if (existingUser != null) {
            throw new UserAlreadyExistsException("User already exists.");
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        userRepository.saveUser(
                request.getEmail(),
                encodedPassword
        );
    }

    public LoginResponseDTO login(LoginRequestDTO request) {

        User user = userRepository.findByEmail(request.getEmail());

        if (user == null ||
                !passwordEncoder.matches(request.getPassword(), user.getPassword())) {

            throw new InvalidCredentialsException(
                    "Invalid email or password."
            );
        }

        String token = jwtService.generateToken(user.getEmail());

        return LoginResponseDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .token(token)
                .build();
    }
}