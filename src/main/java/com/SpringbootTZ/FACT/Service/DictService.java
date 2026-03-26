package com.SpringbootTZ.FACT.Service;

import com.SpringbootTZ.FACT.Entity.SysDict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class DictService {

    private static final Logger log = LoggerFactory.getLogger(DictService.class);

    private final ConfigFileRepository configFileRepository;

    public DictService(ConfigFileRepository configFileRepository) {
        this.configFileRepository = configFileRepository;
    }

    public List<SysDict> getDict(String dictType) {
        if (dictType == null || dictType.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return configFileRepository.getDictByType(dictType.trim());
        } catch (Exception e) {
            log.error("读取数据字典失败，dictType={}", dictType, e);
            return Collections.emptyList();
        }
    }
}

