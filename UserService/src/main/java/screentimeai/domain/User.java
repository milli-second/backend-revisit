package screentimeai.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.domain.AfterDomainEventPublication;
import org.springframework.data.domain.DomainEvents;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
    private String uuid; // 🔥 외부 참조용 글로벌 ID
    private int profile_image_number = 1; // 기본값 1번 프로필


    // ✅ 엔티티 생성 시 자동으로 UUID 부여
    @PrePersist
    public void prePersist() {
        if (this.uuid == null) {
            this.uuid = UUID.randomUUID().toString();
        }
    }
    @Transient
    private final List<Object> domainEvents = new ArrayList<>();

    public void signup(String rawPassword, PasswordEncoder encoder,int profileImageNumber) {
        this.password = encoder.encode(rawPassword);
        this.domainEvents.add(new SignedUp(this));
        this.profile_image_number = profileImageNumber; // ✅ 프로필 번호 설정
    }

    public void login(String rawPassword, PasswordEncoder encoder) {
        if (!encoder.matches(rawPassword, this.password)) {
            throw new IllegalArgumentException("❌ 비밀번호 불일치");
        }
        this.domainEvents.add(new LoggedIn(this));
    }

    public void logout() {
        this.domainEvents.add(new LoggedOut(this));
    }

    public void changePassword(String oldPassword, String newPassword, PasswordEncoder encoder) {
        if (!encoder.matches(oldPassword, this.password)) {
            throw new IllegalArgumentException("❌ 기존 비밀번호가 일치하지 않습니다");
        }
        this.password = encoder.encode(newPassword);
        this.domainEvents.add(new PasswordChanged(this));
    }

    public void deactivate() {
        if (!this.active) {
            throw new IllegalStateException("❌ 이미 탈퇴된 계정입니다.");
        }
        this.active = false;
        this.domainEvents.add(new AccountDeactivated(this));
    }

    @DomainEvents
    public Collection<Object> events() {
        return domainEvents;
    }

    @AfterDomainEventPublication
    public void clearEvents() {
        domainEvents.clear();
    }

}



