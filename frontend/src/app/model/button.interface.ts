export interface ButtonAction {
    label: string;
    action: string;
}

export interface ButtonMenuItem {
    label: string;
    action: string;
    icon?: string;
}

export interface Button {
    label: string;
    icon: string;
    actions: ButtonAction[] | string;
    styleClass: string;
    menuItems?: ButtonMenuItem[];
}
