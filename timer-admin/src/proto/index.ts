export interface MenuTreeVO {
  id: number;
  menuName: string;
  menuCode: string;
  parentId: number | null;
  component: string | (() => Promise<Record<string, any>>);
  routePath: string;
  redirect?: string;
  icon?: string;
  children?: MenuTreeVO[];
  meta?: {
    title: string;
    isDynamic: boolean;
  } 
}