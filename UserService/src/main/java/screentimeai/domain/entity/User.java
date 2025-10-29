package screentimeai.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "User_table")
@Data
//<<< DDD / Aggregate Root

public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nickname;
    private String password;
    private boolean active = true;  //  탈퇴 여부 표시
    private String category = null;//초기에는 null처리

    @Column(unique = true, updatable = false, nullable = false)
    private String subjectId; // 🔥 외부 참조용 글로벌 ID
    private int profileImageNumber = 1; // 기본값 1번 프로필

    // ✅ 자동으로 생성 및 갱신 시간 관리
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;   // 회원가입 시 자동 기록

    @UpdateTimestamp
    private LocalDateTime updatedAt;   // 비밀번호, 닉네임 변경 시 자동 갱신

    // ✅ 엔티티 생성 시 자동으로 UUID 부여
    @PrePersist
    public void prePersist() {
        if (this.subjectId == null) {
            this.subjectId = UUID.randomUUID().toString();
        }
    }

    public void signup(String rawPassword, PasswordEncoder encoder,int profileImageNumber) {
        this.password = encoder.encode(rawPassword);
        this.profileImageNumber = profileImageNumber; // ✅ 프로필 번호 설정
    }

    public void login(String rawPassword, PasswordEncoder encoder) {
        if (!encoder.matches(rawPassword, this.password)) {
            throw new IllegalArgumentException("❌ 비밀번호 불일치");
        }
    }

    public void logout() {
    }

    // ✅ 닉네임 변경
    public void changeNickname(String newNickname) {
        if (newNickname == null || newNickname.isBlank()) {
            throw new IllegalArgumentException("❌ 닉네임은 비워둘 수 없습니다.");
        }
        if (!newNickname.matches("^[a-zA-Z0-9]{5,20}$")) {
            throw new IllegalArgumentException("❌ 닉네임은 영문과 숫자 조합으로 5~20자 이내여야 합니다.");
        }
        this.nickname = newNickname;
    }


    public void changePassword(String oldPassword, String newPassword, PasswordEncoder encoder) {
        if (!encoder.matches(oldPassword, this.password)) {
            throw new IllegalArgumentException("❌ 기존 비밀번호가 일치하지 않습니다");
        }
        this.password = encoder.encode(newPassword);
    }

    // ✅ 프로필 이미지 변경
    public void changeProfileImage(int newProfileImageNumber) {
        if (newProfileImageNumber < 1 || newProfileImageNumber > 8) {
            throw new IllegalArgumentException("❌ 프로필 이미지 번호는 1~8 사이여야 합니다.");
        }
        this.profileImageNumber = newProfileImageNumber;
    }


    public void deactivate() {
        if (!this.active) {
            throw new IllegalStateException("❌ 이미 탈퇴된 계정입니다.");
        }
        this.active = false;
    }

}