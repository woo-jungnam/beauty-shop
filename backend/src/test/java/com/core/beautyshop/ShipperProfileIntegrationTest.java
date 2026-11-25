package com.core.beautyshop;

import com.core.beautyshop.modules.identity.domain.Role;
import com.core.beautyshop.modules.identity.domain.RoleRepository;
import com.core.beautyshop.modules.identity.domain.User;
import com.core.beautyshop.modules.identity.domain.UserRepository;
import com.core.beautyshop.shared.security.services.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shipper_profile_test;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "app.media.upload-dir=${java.io.tmpdir}/beautyshop-shipper-profile-test-uploads"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ShipperProfileIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;

    private User createUser(String roleName) {
        Role role = roles.findByName(roleName)
                .orElseGet(() -> roles.saveAndFlush(Role.builder().name(roleName).build()));
        String suffix = UUID.randomUUID().toString();
        return users.saveAndFlush(User.builder()
                .username("ship_" + suffix.substring(0, 8))
                .email("ship_" + suffix.substring(0, 8) + "@example.test")
                .fullName("Tài xế Test")
                .passwordHash(encoder.encode("Pass1234!"))
                .roles(new ArrayList<>(List.of(role)))
                .build());
    }

    private UsernamePasswordAuthenticationToken authOf(User user) {
        UserDetailsImpl principal = UserDetailsImpl.build(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void shipperCanReadAndUpdateOperationalProfileAndPersonalInfoAndUploadAvatar() throws Exception {
        User shipper = createUser("ROLE_SHIPPER");
        User customer = createUser("ROLE_CUSTOMER");

        mvc.perform(get("/api/v1/shipper/profile"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/shipper/profile").with(authentication(authOf(customer))))
                .andExpect(status().isForbidden());

        // Initially returns empty fields cleanly instead of 404
        mvc.perform(get("/api/v1/shipper/profile").with(authentication(authOf(shipper))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vehicleType").doesNotExist());

        // Reject invalid emergency contact phone
        mvc.perform(put("/api/v1/shipper/profile")
                        .with(authentication(authOf(shipper)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleType":"Xe máy","emergencyContactPhone":"123"}
                                """))
                .andExpect(status().isBadRequest());

        // Save valid shipper operational profile
        mvc.perform(put("/api/v1/shipper/profile")
                        .with(authentication(authOf(shipper)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleType": "Xe máy",
                                  "vehiclePlate": " 59x1-888.99 ",
                                  "serviceArea": "Quận 1, Quận 3",
                                  "emergencyContactName": "Nguyễn Văn B",
                                  "emergencyContactPhone": "0912345678",
                                  "workShift": "Cả ngày"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vehicleType").value("Xe máy"))
                .andExpect(jsonPath("$.data.vehiclePlate").value("59X1-888.99"))
                .andExpect(jsonPath("$.data.serviceArea").value("Quận 1, Quận 3"))
                .andExpect(jsonPath("$.data.emergencyContactPhone").value("0912345678"))
                .andExpect(jsonPath("$.data.workShift").value("Cả ngày"));

        // Upload valid PNG avatar
        byte[] minimalPng = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", minimalPng);
        String uploadResp = mvc.perform(multipart("/api/v1/shipper/profile/avatar")
                        .file(file)
                        .with(authentication(authOf(shipper))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").isString())
                .andReturn().getResponse().getContentAsString();

        int idx = uploadResp.indexOf("/uploads/");
        String url = uploadResp.substring(idx, uploadResp.indexOf('"', idx));
        try {
            mvc.perform(put("/api/v1/users/profile")
                            .with(authentication(authOf(shipper)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("""
                                    {"fullName":"Tài xế Cập Nhật","phone":"0988776655","avatarUrl":"%s","gender":"MALE","dateOfBirth":"1995-05-15"}
                                    """, url)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.fullName").value("Tài xế Cập Nhật"))
                    .andExpect(jsonPath("$.data.phone").value("0988776655"))
                    .andExpect(jsonPath("$.data.avatarUrl").value(url));
        } finally {
            String fileName = url.replace("/uploads/", "");
            Files.deleteIfExists(Path.of(System.getProperty("java.io.tmpdir"), "beautyshop-shipper-profile-test-uploads", fileName));
        }
    }
}

