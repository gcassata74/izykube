import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
import { Link, LinkType } from '../../model/link.class';
import { LinkUpdateService } from '../../services/link-update.service';

@Component({
  selector: 'app-link-properties-form',
  templateUrl: './link-properties-form.component.html',
  styleUrls: ['./link-properties-form.component.scss']
})
export class LinkPropertiesFormComponent implements OnInit, OnChanges, OnDestroy {
  @Input() link!: Link;

  form!: FormGroup;
  private autosaveSub?: Subscription;

  constructor(
    private fb: FormBuilder,
    private linkUpdateService: LinkUpdateService
  ) {}

  ngOnInit(): void {
    this.initForm();
    this.setupAutosave();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['link'] && !changes['link'].firstChange) {
      this.patchForm(changes['link'].currentValue as Link);
      this.restartAutosave();
    }
  }

  get selectedType(): LinkType {
    return this.form?.get('linkType')?.value ?? 'Expose';
  }

  private initForm(): void {
    this.form = this.fb.group({
      linkType: [this.link?.type ?? 'Expose', Validators.required],
      note: [this.link?.note ?? '']
    });
  }

  private patchForm(link: Link): void {
    if (!this.form) {
      return;
    }
    this.form.patchValue(
      {
        linkType: link?.type ?? 'Expose',
        note: link?.note ?? ''
      },
      { emitEvent: false }
    );
  }

  private setupAutosave(): void {
    if (!this.form || !this.link?.id) {
      return;
    }
    this.autosaveSub = this.linkUpdateService.setupAutosave(
      this.form,
      this.link.id,
      this.form.valueChanges
    );
  }

  private restartAutosave(): void {
    if (this.autosaveSub) {
      this.autosaveSub.unsubscribe();
    }
    this.setupAutosave();
  }

  ngOnDestroy(): void {
    this.autosaveSub?.unsubscribe();
  }
}
