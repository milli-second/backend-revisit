package screentimeai.application;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Package name을 기반으로 카테고리를 매핑하는 서비스
 * all_apps.csv 파일을 로드하여 package_name → category_id 매핑 제공
 */
@Service
@Slf4j
public class CategoryMappingService {

    // Category ID 정의
    public static final int CATEGORY_GAME = 1;
    public static final int CATEGORY_SOCIAL_COMM = 2;
    public static final int CATEGORY_LEARNING = 3;
    public static final int CATEGORY_ENTERTAINMENT = 4;
    public static final int CATEGORY_OTHER = 5;

    // Category Name
    public static final String CATEGORY_NAME_GAME = "GAME";
    public static final String CATEGORY_NAME_SOCIAL_COMM = "SOCIAL_COMM";
    public static final String CATEGORY_NAME_LEARNING = "LEARNING";
    public static final String CATEGORY_NAME_ENTERTAINMENT = "ENTERTAINMENT";
    public static final String CATEGORY_NAME_OTHER = "OTHER";

    private final Map<String, Integer> packageToCategoryMap = new HashMap<>();
    private final Map<String, String> packageToCategoryCodeMap = new HashMap<>();

    /**
     * 애플리케이션 시작 시 CSV 파일을 로드하여 매핑 테이블 구성
     */
    @PostConstruct
    public void init() {
        try {
            loadCategoryMappings();
            log.info("Category mappings loaded successfully: {} packages", packageToCategoryMap.size());
        } catch (Exception e) {
            log.error("Failed to load category mappings", e);
            // 서비스 시작은 계속되도록 하되, 매핑 실패는 로그로만 남김
        }
    }

    /**
     * CSV 파일에서 카테고리 매핑 정보 로드
     */
    private void loadCategoryMappings() throws Exception {
        ClassPathResource resource = new ClassPathResource("all_apps.csv");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                // 헤더 스킵
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                String[] parts = line.split(",", 2);
                if (parts.length != 2) continue;

                String packageName = parts[0].trim();
                String categoryCode = parts[1].trim();

                int categoryId = mapCategoryCodeToCategoryId(categoryCode);
                packageToCategoryMap.put(packageName, categoryId);
                packageToCategoryCodeMap.put(packageName, categoryCode);
            }
        }
    }

    /**
     * Google Play Store 카테고리 코드를 우리의 카테고리 ID로 매핑
     */
    private int mapCategoryCodeToCategoryId(String categoryCode) {
        return switch (categoryCode) {
            case "GAME" -> CATEGORY_GAME;

            case "SOCIAL", "COMMUNICATION", "DATING" -> CATEGORY_SOCIAL_COMM;

            case "EDUCATION", "BOOKS_AND_REFERENCE" -> CATEGORY_LEARNING;

            case "ENTERTAINMENT", "VIDEO_PLAYERS", "COMICS" -> CATEGORY_ENTERTAINMENT;

            default -> CATEGORY_OTHER;
        };
    }

    /**
     * Package name으로 카테고리 ID 조회
     */
    public int getCategoryId(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return CATEGORY_OTHER;
        }
        return packageToCategoryMap.getOrDefault(packageName, CATEGORY_OTHER);
    }

    /**
     * 카테고리 ID로 카테고리 이름 조회
     */
    public String getCategoryName(int categoryId) {
        return switch (categoryId) {
            case CATEGORY_GAME -> CATEGORY_NAME_GAME;
            case CATEGORY_SOCIAL_COMM -> CATEGORY_NAME_SOCIAL_COMM;
            case CATEGORY_LEARNING -> CATEGORY_NAME_LEARNING;
            case CATEGORY_ENTERTAINMENT -> CATEGORY_NAME_ENTERTAINMENT;
            default -> CATEGORY_NAME_OTHER;
        };
    }

    /**
     * 카테고리 ID가 SNS/소셜 카테고리인지 확인
     */
    public boolean isSocialCategory(int categoryId) {
        return categoryId == CATEGORY_SOCIAL_COMM;
    }

    /**
     * Package name이 SNS/소셜 앱인지 확인
     */
    public boolean isSocialApp(String packageName) {
        int categoryId = getCategoryId(packageName);
        return isSocialCategory(categoryId);
    }

    /**
     * Package name으로 원본 Google Play 카테고리 코드 조회
     * @param packageName 패키지명
     * @return 카테고리 코드 (예: GAME, SOCIAL, ART_AND_DESIGN 등)
     */
    public String getCategoryCode(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return "OTHER";
        }
        return packageToCategoryCodeMap.getOrDefault(packageName, "OTHER");
    }
}
