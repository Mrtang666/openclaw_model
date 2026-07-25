package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.netdisk.service.NetdiskToolService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NetdiskWechatToolTests {

    @Test
    void authToolDelegatesBindOperationToNetdiskService() {
        RecordingNetdiskToolService service = new RecordingNetdiskToolService();
        NetdiskAuthWechatTool tool = new NetdiskAuthWechatTool(service);

        var reply = tool.execute(request(Map.of("operation", "bind")));

        assertThat(reply.text()).isEqualTo("bind-result");
        assertThat(service.operation).isEqualTo("bind");
        assertThat(service.userId).isEqualTo("wx-user-1");
    }

    @Test
    void searchToolDelegatesSearchArgumentsToNetdiskService() {
        RecordingNetdiskToolService service = new RecordingNetdiskToolService();
        NetdiskSearchWechatTool tool = new NetdiskSearchWechatTool(service);

        var reply = tool.execute(request(Map.of("query", "项目文档", "mode", "keyword", "dir", "/", "limit", "3")));

        assertThat(reply.text()).isEqualTo("search-result");
        assertThat(service.query).isEqualTo("项目文档");
        assertThat(service.mode).isEqualTo("keyword");
        assertThat(service.limit).isEqualTo(3);
    }

    private WechatToolRequest request(Map<String, String> arguments) {
        return new WechatToolRequest("wx-user-1", "用户原始需求", arguments, "", null, null);
    }

    private static final class RecordingNetdiskToolService implements NetdiskToolService {

        private String operation;
        private String userId;
        private String query;
        private String mode;
        private int limit;

        @Override
        public String auth(String userId, String operation) {
            this.userId = userId;
            this.operation = operation;
            return "bind-result";
        }

        @Override
        public String search(String userId, String query, String mode, String dir, int limit) {
            this.userId = userId;
            this.query = query;
            this.mode = mode;
            this.limit = limit;
            return "search-result";
        }

        @Override
        public String list(String userId, String dir, int page) {
            return "list-result";
        }

        @Override
        public String share(String userId, String fsidList, int period, String pwd) {
            return "share-result";
        }

        @Override
        public String saveText(String userId, String content, String dir, String filename) {
            return "save-result";
        }
    }
}
