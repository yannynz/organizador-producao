import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'filesize',
  standalone: true,
})
export class FilesizePipe implements PipeTransform {
  transform(value?: number | null, fractionDigits = 1): string {
    if (value === null || value === undefined || value < 0) {
      return '-';
    }

    if (value === 0) {
      return '0 B';
    }

    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    let unitIndex = 0;
    let output = value;

    while (output >= 1024 && unitIndex < units.length - 1) {
      output /= 1024;
      unitIndex += 1;
    }

    const fixed = output.toFixed(fractionDigits);
    const normalized = fractionDigits > 0 ? fixed.replace(/\.0+$/, '').replace(/(\.[0-9]*[1-9])0+$/, '$1') : fixed;
    return `${normalized} ${units[unitIndex]}`;
  }
}
