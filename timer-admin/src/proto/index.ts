export interface MenuTreeVO {
  id: number;
  menuName: string;
  menuCode: string;
  parentId: number | null;
  routePath: string;
  icon?: string;
  children?: MenuTreeVO[];
}