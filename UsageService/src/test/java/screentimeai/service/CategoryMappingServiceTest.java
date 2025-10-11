package screentimeai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CategoryMappingService.class)
class CategoryMappingServiceTest {

    @Autowired
    private CategoryMappingService categoryMappingService;

    @Test
    @DisplayName("CSV 파일에서 카테고리 매핑 로드")
    void testCategoryMappingLoaded() {
        // Given: CSV에 있는 실제 패키지 이름들
        String canvaPackage = "com.canva.editor";  // ART_AND_DESIGN → OTHER
        String bandPackage = "com.nhn.android.band";  // 확인 필요

        // When
        int canvaCategory = categoryMappingService.getCategoryId(canvaPackage);
        int bandCategory = categoryMappingService.getCategoryId(bandPackage);

        // Then
        assertThat(canvaCategory).isNotNull();
        assertThat(bandCategory).isNotNull();
        assertThat(canvaCategory).isEqualTo(5);
        assertThat(bandCategory).isEqualTo(2);

    }

    @Test
    @DisplayName("알 수 없는 패키지는 OTHER 카테고리로 분류")
    void testUnknownPackageReturnsOther() {
        // Given
        String unknownPackage = "com.unknown.test.app";

        // When
        int categoryId = categoryMappingService.getCategoryId(unknownPackage);

        // Then
        assertThat(categoryId).isEqualTo(CategoryMappingService.CATEGORY_OTHER);
    }

    @Test
    @DisplayName("GAME 카테고리 매핑 확인")
    void testGameCategoryMapping() {
        // Given
        String sudoku = "com.mocacorp.sudoku";

        // When
        int categoryId = categoryMappingService.getCategoryId(sudoku);

        // Then
        assertThat(categoryId).isEqualTo(CategoryMappingService.CATEGORY_GAME);
    }

    @Test
    @DisplayName("SOCIAL_COMM 카테고리 매핑 확인")
    void testSocialCommCategoryMapping() {
        // Given
        String vapp = "com.naver.vapp";
        String phone = "com.skt.prod.phonebook";

        // When
        int vappCategoryID = categoryMappingService.getCategoryId(vapp);
        int phoneCategoryID = categoryMappingService.getCategoryId(phone);


        // Then
        assertThat(vappCategoryID).isEqualTo(CategoryMappingService.CATEGORY_SOCIAL_COMM);
        assertThat(phoneCategoryID).isEqualTo(CategoryMappingService.CATEGORY_SOCIAL_COMM);
    }

    @Test
    @DisplayName("LEARNING 카테고리 매핑 확인")
    void testLearningCategoryMapping() {
        // When
        String categoryName = categoryMappingService.getCategoryName(
                CategoryMappingService.CATEGORY_LEARNING);

        // Then
        assertThat(categoryName).isEqualTo("LEARNING");
    }

    @Test
    @DisplayName("ENTERTAINMENT 카테고리 매핑 확인")
    void testEntertainmentCategoryMapping() {
        // When
        String categoryName = categoryMappingService.getCategoryName(
                CategoryMappingService.CATEGORY_ENTERTAINMENT);

        // Then
        assertThat(categoryName).isEqualTo("ENTERTAINMENT");
    }

    @Test
    @DisplayName("카테고리 ID 범위 확인")
    void testCategoryIdRange() {
        // Given: 모든 카테고리 ID는 1-5 사이여야 함
        String unknownPackage = "com.test.unknown";
        String app1 = "com.samsung.android.voc";
        String app2 = "com.fridaynoons.playwings";
        String app3 = "com.skb.btvmobile";

        // When
        int categoryId = categoryMappingService.getCategoryId(unknownPackage);
        int categoryId2 = categoryMappingService.getCategoryId(app1);
        int categoryId3 = categoryMappingService.getCategoryId(app2);
        int categoryId4 = categoryMappingService.getCategoryId(app3);

        // Then
        assertThat(categoryId).isBetween(1, 5);
        assertThat(categoryId2).isBetween(1, 5);
        assertThat(categoryId3).isBetween(1, 5);
        assertThat(categoryId4).isBetween(1, 5);
    }

    @Test
    @DisplayName("NULL 패키지명 처리")
    void testNullPackageHandling() {
        // When
        int categoryId = categoryMappingService.getCategoryId(null);

        // Then
        assertThat(categoryId).isEqualTo(CategoryMappingService.CATEGORY_OTHER);
    }
}
