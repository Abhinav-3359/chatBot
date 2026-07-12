package rag.example.rag_implementation.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import rag.example.rag_implementation.dto.LoginRequestDTO;
import rag.example.rag_implementation.dto.RegisterRequestDTO;
import rag.example.rag_implementation.model.User;
import rag.example.rag_implementation.repository.UserRepository;

@Service
public class AuthService {
    @Autowired
    UserService userService;
    @Autowired
    UserRepository userRepository;

    public String register(RegisterRequestDTO request) {

        User existing = userRepository.findByEmail(request.getEmail());

        if (existing != null) {
            return "User already exists";
        }

        userRepository.saveUser(request.getEmail(), request.getPassword());

        return "User registered successfully";
    }

    public String login(LoginRequestDTO request) {
        User user = userRepository.findByEmail(request.getEmail());

        if (user == null) {
            return "User not found";
        }

        if (!user.getPassword().equals(request.getPassword())) {
            return "Invalid password";
        }

        return "Login successful";
    }

}
