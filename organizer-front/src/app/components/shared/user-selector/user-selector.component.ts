import { Component, Input, forwardRef, OnInit } from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { AssignableUser } from '../../../models/user.model';

@Component({
  selector: 'app-user-selector',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './user-selector.component.html',
  styleUrls: ['./user-selector.component.css'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => UserSelectorComponent),
      multi: true
    }
  ]
})
export class UserSelectorComponent implements ControlValueAccessor, OnInit {
  @Input() availableUsers: AssignableUser[] = [];
  @Input() placeholder: string = 'Selecionar responsável';
  @Input() allowManual: boolean = false;

  selectedNames: string[] = [];
  disabled = false;
  manualName = '';

  onChange: any = () => {};
  onTouched: any = () => {};

  ngOnInit() {}

  // ControlValueAccessor implementation
  writeValue(value: any): void {
    if (value) {
      this.selectedNames = value.toString().split(' / ').filter((s: string) => s.trim().length > 0);
    } else {
      this.selectedNames = [];
    }
  }

  registerOnChange(fn: any): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: any): void {
    this.onTouched = fn;
  }

  setDisabledState?(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }

  // Actions
  addName(name: string) {
    const cleaned = name.trim();
    if (cleaned.length < 2 || this.hasName(cleaned)) {
      return;
    }
    this.selectedNames.push(cleaned);
    this.triggerChange();
  }

  removeName(name: string) {
    this.selectedNames = this.selectedNames.filter(n => n !== name);
    this.triggerChange();
  }

  addManual(event?: Event) {
    if (event) {
      event.preventDefault();
    }
    if (!this.allowManual || this.disabled) {
      return;
    }
    const cleaned = this.manualName.trim();
    if (cleaned.length < 2) {
      return;
    }
    this.addName(cleaned);
    this.manualName = '';
  }

  private triggerChange() {
    const joined = this.selectedNames.join(' / ');
    this.onChange(joined);
    this.onTouched();
  }

  private hasName(name: string): boolean {
    const target = name.toLowerCase();
    return this.selectedNames.some(n => n.toLowerCase() === target);
  }

  get unselectedUsers(): AssignableUser[] {
    const selected = new Set(this.selectedNames.map(n => n.toLowerCase()));
    return this.availableUsers.filter(u => !selected.has(u.name.toLowerCase()));
  }
}
