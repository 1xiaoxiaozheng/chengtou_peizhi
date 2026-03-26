package com.SpringbootTZ.FACT.Tools;

import com.SpringbootTZ.FACT.Service.FormMetaSqlGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 不依赖 Spring Boot 启动的生成器入口（用于没有数据库/启动失败时仍可生成 SQL 产物）。
 *
 * 用法：
 * - 无参数：输出到 user.dir/fact-generated-sql/meta_yyyyMMdd_HHmmss
 * - 传一个参数：指定输出根目录（会自动创建 meta_... 子目录）
 */
public class MetaSqlGenerateCli {

    public static void main(String[] args) throws Exception {
        String outDir = (args != null && args.length > 0) ? args[0] : null;
        FormMetaSqlGenerator g = new FormMetaSqlGenerator(new ObjectMapper());
        FormMetaSqlGenerator.GenerateResult r =
                (outDir == null || outDir.trim().isEmpty()) ? g.generateToDefaultDir() : g.generateToDir(outDir.trim());

        System.out.println("outDir=" + r.getOutDir());
        System.out.println("files=" + r.getFiles().size());
        for (String f : r.getFiles()) {
            System.out.println(" - " + f);
        }
        if (r.getWarnings() != null && !r.getWarnings().isEmpty()) {
            System.out.println("warnings=" + r.getWarnings().size());
            for (String w : r.getWarnings()) {
                System.out.println(" ! " + w);
            }
        }
    }
}

