import { Injectable } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { Store } from '@ngrx/store';
import { Observable, Subject, Subscription, debounceTime, distinctUntilChanged, filter, map } from 'rxjs';
import { LinkType } from '../model/link.class';
import { ContainerRole, toContainerRole } from '../model/container.class';
import * as actions from '../store/actions/actions';

interface LinkUpdatePayload {
  type: LinkType;
  note?: string;
  containerRole?: ContainerRole;
  clearContainerRole?: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class LinkUpdateService {
  private redrawSubject = new Subject<{ linkId: string; changes: LinkUpdatePayload }>();
  readonly redraw$ = this.redrawSubject.asObservable();

  constructor(private store: Store) {}

  setupAutosave(form: FormGroup, linkId: string, change$?: Observable<any>): Subscription {
    const valueChanges$ = change$ ?? form.valueChanges;
    return valueChanges$.pipe(
      debounceTime(400),
      filter(() => form.valid),
      map((raw) => this.normalizePayload(raw)),
      filter((payload): payload is LinkUpdatePayload => !!payload && !!payload.type),
      distinctUntilChanged((a, b) =>
        a.type === b.type &&
        a.note === b.note &&
        a.containerRole === b.containerRole &&
        !!a.clearContainerRole === !!b.clearContainerRole
      )
    ).subscribe((payload) => {
      this.updateLink(linkId, payload);
    });
  }

  updateLink(linkId: string, changes: Partial<LinkUpdatePayload>): void {
    const payload = this.normalizePayload(changes);
    if (!payload) {
      return;
    }
    this.store.dispatch(actions.updateLink({ linkId, changes: payload }));
    this.redrawSubject.next({ linkId, changes: payload });
  }

  private normalizePayload(raw: any): LinkUpdatePayload | null {
    const type = raw?.linkType ?? raw?.type;
    const typeString = String(type ?? '').trim();
    const lowerType = typeString.toLowerCase();
    const normalizedType: LinkType =
      typeString === 'Use'
        ? 'Use'
        : typeString === 'Container'
          ? 'Container'
          : lowerType === 'serviceaccountbinding'
            ? 'serviceAccountBinding'
            : lowerType === 'appliesto'
              ? 'appliesTo'
              : 'Expose';
    const note = typeof raw?.note === 'string' ? raw.note : raw?.note === '' ? '' : undefined;
    const containerRoleKeyPresent = raw != null && Object.prototype.hasOwnProperty.call(raw, 'containerRole');
    const normalizedRole = toContainerRole(raw?.containerRole);
    const next: LinkUpdatePayload = {
      type: normalizedType,
      ...(note !== undefined ? { note } : {})
    };

    if (containerRoleKeyPresent) {
      if (normalizedType !== 'Container') {
        next.clearContainerRole = true;
      } else if (normalizedRole) {
        next.containerRole = normalizedRole;
        next.clearContainerRole = false;
      } else {
        next.clearContainerRole = true;
      }
    }

    return next;
  }
}
