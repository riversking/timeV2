# Vue 3 + TypeScript + Vite

This template should help get you started developing with Vue 3 and TypeScript in Vite. The template uses Vue 3 `<script setup>` SFCs, check out the [script setup docs](https://v3.vuejs.org/api/sfc-script-setup.html#sfc-script-setup) to learn more.

Learn more about the recommended Project Setup and IDE Support in the [Vue Docs TypeScript Guide](https://vuejs.org/guide/typescript/overview.html#project-setup).

npm install vue3-emoji-picker
npm install emoji-mart @emoji-mart/data


import "emoji-mart/css/emoji-mart.css";
import { Picker } from "emoji-mart";
import data from "emoji-mart/data/apple.json";

// 删掉 init({ data })  —— v3 没有 init
// 删掉 import { init } from "emoji-mart"

const emojiContainerRef = ref<HTMLElement | null>(null);
let pickerInstance: any = null;

const onSelectEmoji = (emojiData: any) => {
  userMessage.value += emojiData.native || "";
  showEmoji.value = false;
  nextTick(() => {
    msgInputRef.value?.focus();
  });
};

watch(showEmoji, (val) => {
  if (val) {
    nextTick(() => {
      if (!pickerInstance && emojiContainerRef.value) {
        pickerInstance = new Picker({
          data,
          set: "apple",
          sheetSize: 64,
          emojiSize: 24,
          emojiButtonSize: 32,
          backgroundImageFn: (set: string, sheetSize: number) => {
            return `/img/${set}-${sheetSize}.png`;  // → /img/apple-64.png
          },
          onSelect: onSelectEmoji,  // ← v3 是 onSelect，不是 onEmojiSelect
        });
        emojiContainerRef.value.appendChild(pickerInstance);
      }
    });
  } else {
    if (pickerInstance) {
      pickerInstance.remove();
      pickerInstance = null;
    }
  }
});