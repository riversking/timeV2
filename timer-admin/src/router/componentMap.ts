// src/router/componentMap.ts
import type { Component } from "vue";

// 使用类型断言确保正确类型
const viewsModules = import.meta.glob("@/views/**/*.vue", {
  eager: true,
}) as Record<string, () => Promise<{ default: Component }>>;

// 创建组件映射表
export const componentMap: Record<string, () => Promise<Component>> = {};
Object.keys(viewsModules).forEach((path) => {
  // 提取文件名（不带路径和扩展名）
  const fileName = path.split("/").pop()?.replace(".vue", "");

  if (fileName) {
    // ✅ 关键修复：将驼峰式文件名转换为 kebab-case
    const kebabCaseName = fileName
      .replace(/([a-z])([A-Z])/g, "$1-$2")
      .toLowerCase();

    // 使用转换后的 kebab-case 作为映射键
    componentMap[kebabCaseName] = () =>
      viewsModules[path]().then((module) => module.default as Component);
  }
});
