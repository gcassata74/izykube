import { Injectable } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { Store } from '@ngrx/store';
import { Observable, Subject, Subscription, debounceTime, distinctUntilChanged, filter, map } from 'rxjs';
import { LinkType } from '../model/link.class';
import * as actions from '../store/actions/actions';

interface LinkUpdatePayload {
  type: LinkType;
  note?: string;
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
      distinctUntilChanged((a, b) => a.type === b.type && a.note === b.note)
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
    const normalizedType: LinkType = type === 'Use' ? 'Use' : 'Expose';
    const note = typeof raw?.note === 'string' ? raw.note : raw?.note === '' ? '' : undefined;
    return { type: normalizedType, ...(note !== undefined ? { note } : {}) };
  }
}
