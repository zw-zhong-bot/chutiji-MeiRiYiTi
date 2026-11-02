package com.chutiji.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chutiji.common.ApiResult;
import com.chutiji.module.api.ModuleMeta;
import com.chutiji.module.registry.ModuleRegistry;

@RestController
@RequestMapping("/api/modules")
public class ModuleController {

    private final ModuleRegistry registry;

    public ModuleController(ModuleRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public ApiResult<List<ModuleMeta>> list() {
        return ApiResult.ok(registry.metas());
    }
}
