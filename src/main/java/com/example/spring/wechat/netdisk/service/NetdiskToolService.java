package com.example.spring.wechat.netdisk.service;

/**
 * 微信网盘工具服务接口。
 *
 * <p>微信 Function Calling 工具只依赖这个接口，不直接操作 OAuth、token 解密和 MCP。
 * 这样工具层保持很薄，后续换网盘或换 MCP 客户端时不需要改工具协议。</p>
 */
public interface NetdiskToolService {

    String auth(String userId, String operation);

    String search(String userId, String query, String mode, String dir, int limit);

    String list(String userId, String dir, int page);

    String share(String userId, String fsidList, int period, String pwd);

    String saveText(String userId, String content, String dir, String filename);
}
