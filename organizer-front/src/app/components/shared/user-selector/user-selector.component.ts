import { Component, Input, forwardRef, OnInit } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { User } from '../../../models/user.model';

@Component({
  selector: 'app-user-selector',
  standalone: true,
  imports: [CommonModule],
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
  @Input() availableUsers: User[] = [];
  @Input() placeholder: string = 'Selecionar responsável';

  selectedNames: string[] = [];
  disabled = false;

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
    if (!this.selectedNames.includes(name)) {
      this.selectedNames.push(name);
      this.triggerChange();
    }
  }

  removeName(name: string) {
    this.selectedNames = this.selectedNames.filter(n => n !== name);
    this.triggerChange();
  }

  private triggerChange() {
    const joined = this.selectedNames.join(' / ');
    this.onChange(joined);
    this.onTouched();
  }

  get unselectedUsers(): User[] {
    return this.availableUsers.filter(u => !this.selectedNames.includes(u.name));
  }
}
