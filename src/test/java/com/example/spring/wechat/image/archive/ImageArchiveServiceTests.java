package com.example.spring.wechat.image.archive;

import com.example.spring.wechat.image.generation.model.ImageGenerationResult;
import com.example.spring.wechat.model.ImageSourceType;
import com.example.spring.wechat.model.WechatIncomingImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImageArchiveServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void archivesUserImagesAndLoadsPendingImagesWithBytes() {
        ImageArchiveService service = new ImageArchiveService(null, tempDir, 5);
        WechatIncomingImage first = new WechatIncomingImage(
                ImageSourceType.WECHAT_ATTACHMENT,
                "wechat://msg-1/1",
                "first".getBytes(),
                "image/png",
                "first.png",
                10,
                20,
                "COLOR");

        List<ArchivedWechatImage> archived = service.archiveUserImages("user-1", "msg-1", List.of(first));

        assertThat(archived).hasSize(1);
        assertThat(archived.get(0).imageIndex()).isEqualTo(1);
        assertThat(service.pendingImages("user-1")).hasSize(1);
        assertThat(service.pendingWechatImages("user-1").get(0).bytes()).isEqualTo("first".getBytes());
    }

    @Test
    void archivesGeneratedImagesAsConversationResources() {
        ImageArchiveService service = new ImageArchiveService(null, tempDir, 5);
        ImageGenerationResult result = new ImageGenerationResult(
                "一只橘猫",
                "https://example.com/cat.png",
                "cat".getBytes(),
                "cat.png",
                "image/png",
                1024,
                1024);

        ArchivedWechatImage archived = service.archiveGeneratedImage("user-1", result);

        assertThat(archived.sourceType()).isEqualTo(ImageArchiveSourceType.AI_GENERATED);
        assertThat(archived.prompt()).isEqualTo("一只橘猫");
        assertThat(service.latestGeneratedWechatImage("user-1")).isPresent();
    }

    @Test
    void splitsImagesIntoBatchesByConfiguredBatchSize() {
        ImageArchiveService service = new ImageArchiveService(null, tempDir, 5);

        List<List<WechatIncomingImage>> batches = service.batches(List.of(
                image("1"), image("2"), image("3"), image("4"), image("5"),
                image("6"), image("7"), image("8"), image("9"), image("10"), image("11")));

        assertThat(batches).hasSize(3);
        assertThat(batches.get(0)).hasSize(5);
        assertThat(batches.get(1)).hasSize(5);
        assertThat(batches.get(2)).hasSize(1);
    }

    @Test
    void usedImagesRemainAvailableForLaterReferences() {
        ImageArchiveService service = new ImageArchiveService(null, tempDir, 5);
        List<ArchivedWechatImage> archived = service.archiveUserImages(
                "user-1",
                "msg-1",
                List.of(image("1"), image("2"), image("3")));

        service.markUsed("user-1", archived);

        assertThat(service.pendingImages("user-1")).isEmpty();
        assertThat(service.availableImages("user-1")).hasSize(3);
        assertThat(service.availableWechatImages("user-1")).hasSize(3);
        assertThat(service.imageResourceContext("user-1"))
                .contains("当前可用图片资源")
                .contains("已处理");
    }

    @Test
    void cleansExpiredInMemoryImagesAndLocalFiles() throws Exception {
        ImageArchiveService service = new ImageArchiveService(null, tempDir, 5);
        List<ArchivedWechatImage> archived = service.archiveUserImages("user-1", "msg-old", List.of(image("old")));
        Path localFile = Path.of(archived.get(0).localPath());

        ImageArchiveCleanupResult result = service.cleanExpiredImages(
                Instant.now().plusSeconds(10),
                0);

        assertThat(result.deletedMetadata()).isEqualTo(1);
        assertThat(result.deletedLocalFiles()).isEqualTo(1);
        assertThat(Files.exists(localFile)).isFalse();
        assertThat(service.availableImages("user-1")).isEmpty();
    }

    private WechatIncomingImage image(String name) {
        return new WechatIncomingImage(
                ImageSourceType.WECHAT_ATTACHMENT,
                "wechat://" + name,
                name.getBytes(),
                "image/png",
                name + ".png",
                null,
                null,
                null);
    }
}
