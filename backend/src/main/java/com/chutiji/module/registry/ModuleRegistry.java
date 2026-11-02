package com.chutiji.module.registry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.chutiji.common.BizException;
import com.chutiji.module.api.ModuleMeta;
import com.chutiji.module.api.QuestionModule;

/** 收集所有 QuestionModule 实现，按 key 索引。 */
@Component
public class ModuleRegistry {

    private final Map<String, QuestionModule> modules = new LinkedHashMap<>();

    public ModuleRegistry(List<QuestionModule> discovered) {
        for (QuestionModule module : discovered) {
            QuestionModule previous = modules.put(module.key(), module);
            if (previous != null) {
                throw new IllegalStateException("模块 key 重复: " + module.key());
            }
        }
    }

    public QuestionModule require(String key) {
        QuestionModule module = modules.get(key);
        if (module == null) {
            throw new BizException(2002, "模块不存在: " + key);
        }
        return module;
    }

    public Optional<QuestionModule> find(String key) {
        return Optional.ofNullable(modules.get(key));
    }

    public List<ModuleMeta> metas() {
        return modules.values().stream().map(QuestionModule::meta).toList();
    }
}
