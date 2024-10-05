import { ContextMenuModule } from 'primeng/contextmenu';

export interface ButtonAction {
    label: string;
    action: string;
}


export interface Button {
 label: string;
 icon: string;
 actions: ButtonAction[] | string;
 styleClass: string;
}
