package rag.example.rag_implementation.controller;

import org.springframework.web.bind.annotation.*;
import rag.example.rag_implementation.model.User;
import rag.example.rag_implementation.services.UserService;
import rag.example.rag_implementation.dto.LoginRequestDTO;
import rag.example.rag_implementation.dto.RegisterRequestDTO;
import rag.example.rag_implementation.services.AuthService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {
    @Autowired
    AuthService authService;

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<User> getUsers() {
        return userService.getUsers();
    }

    @PostMapping("/register")
    public String registerUser(@RequestBody RegisterRequestDTO user) {
        String result = authService.register(user);
        return result;
    }

    @PostMapping("/login")
    public String LoginUser(@RequestBody LoginRequestDTO user) {
        String result = authService.login(user);
        return result;

    }
}