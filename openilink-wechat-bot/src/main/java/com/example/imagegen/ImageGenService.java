package com.example.imagegen;

import com.example.LocalLLMService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;


public class ImageGenService {


    private static final Logger log =
            LoggerFactory.getLogger(ImageGenService.class);


    private final String apiKey;

    private final String model;

    private final HttpClient httpClient;

    private final ObjectMapper objectMapper;



    public ImageGenService() {


        LocalLLMService.Config cfg =
                LocalLLMService.getConfig();


        this.apiKey =
                cfg.getApiKey();


        this.model =
                cfg.getImageGenModel();



        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofSeconds(30)
                        )
                        .build();



        this.objectMapper =
                new ObjectMapper()
                        .configure(
                                com.fasterxml.jackson.databind
                                        .DeserializationFeature
                                        .FAIL_ON_UNKNOWN_PROPERTIES,
                                false
                        );

    }





    public ImageGenResult generate(String prompt){

        return generate(
                prompt,
                1,
                "1024x1024"
        );

    }





    public ImageGenResult generate(
            String prompt,
            int n,
            String size
    ){


        try {


            ObjectNode body =
                    objectMapper
                            .createObjectNode();



            body.put(
                    "model",
                    model
            );


            body.put(
                    "prompt",
                    prompt
            );


            body.put(
                    "n",
                    n
            );


            body.put(
                    "size",
                    size
            );



            String requestBody =
                    objectMapper
                            .writeValueAsString(body);




            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            "https://api.siliconflow.cn/v1/images/generations"
                                    ))
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .header(
                                    "Authorization",
                                    "Bearer "+apiKey
                            )
                            .timeout(
                                    Duration.ofSeconds(120)
                            )
                            .POST(
                                    HttpRequest.BodyPublishers
                                            .ofString(requestBody)
                            )
                            .build();





            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers
                                    .ofString()
                    );





            if(response.statusCode()!=200){


                log.error(
                        "图片生成失败:{}",
                        response.body()
                );


                return new ImageGenResult(
                        false,
                        "API错误:"+response.statusCode(),
                        null,
                        null
                );

            }





            var root =
                    objectMapper
                            .readTree(
                                    response.body()
                            );


            var data =
                    root.path("data");




            if(data.isArray()
                    && data.size()>0){


                String imageUrl =
                        data.get(0)
                                .path("url")
                                .asText(null);



                if(imageUrl!=null
                        && !imageUrl.isEmpty()){


                    Path tempFile =
                            downloadToTemp(
                                    imageUrl
                            );



                    if(tempFile!=null){


                        return new ImageGenResult(
                                true,
                                "图片生成成功",
                                tempFile,
                                imageUrl
                        );

                    }

                }

            }



            return new ImageGenResult(
                    false,
                    "没有获取图片地址",
                    null,
                    null
            );



        }catch(Exception e){


            log.error(
                    "图片生成异常",
                    e
            );


            return new ImageGenResult(
                    false,
                    e.getMessage(),
                    null,
                    null
            );

        }

    }








    /**
     * 下载图片到临时文件
     * 供微信CDN上传
     */
    private Path downloadToTemp(
            String imageUrl
    ) throws Exception {



        HttpURLConnection conn =
                (HttpURLConnection)
                        new URL(imageUrl)
                                .openConnection();



        conn.setConnectTimeout(
                15000
        );


        conn.setReadTimeout(
                60000
        );



        try(
                InputStream is =
                        conn.getInputStream()
        ){

            byte[] bytes =
                    readBytes(is);



            Path temp =
                    Files.createTempFile(
                            "clawbot_image_",
                            ".png"
                    );


            Files.write(
                    temp,
                    bytes
            );


            log.info(
                    "临时图片:{}",
                    temp
            );


            return temp;


        }
        finally{

            conn.disconnect();

        }


    }





    private byte[] readBytes(
            InputStream is
    ) throws Exception {



        ByteArrayOutputStream bos =
                new ByteArrayOutputStream();



        byte[] buffer =
                new byte[8192];


        int len;



        while(
                (len=is.read(buffer))!=-1
        ){

            bos.write(
                    buffer,
                    0,
                    len
            );

        }


        return bos.toByteArray();

    }








    public static class ImageGenResult {


        private final boolean success;


        private final String message;


        private final Path filePath;


        private final String imageUrl;



        public ImageGenResult(
                boolean success,
                String message,
                Path filePath,
                String imageUrl
        ){

            this.success =
                    success;

            this.message =
                    message;

            this.filePath =
                    filePath;

            this.imageUrl =
                    imageUrl;

        }



        public boolean isSuccess(){

            return success;

        }



        public String getMessage(){

            return message;

        }



        public Path getFilePath(){

            return filePath;

        }



        public String getImageUrl(){

            return imageUrl;

        }

    }

}