import { FilesizePipe } from './filesize.pipe';

describe('FilesizePipe', () => {
  const pipe = new FilesizePipe();

  it('returns dash for invalid values', () => {
    expect(pipe.transform(null)).toBe('-');
    expect(pipe.transform(undefined)).toBe('-');
    expect(pipe.transform(-1)).toBe('-');
  });

  it('formats bytes to human readable units', () => {
    expect(pipe.transform(0)).toBe('0 B');
    expect(pipe.transform(1024)).toBe('1 KB');
    expect(pipe.transform(1536)).toBe('1.5 KB');
    expect(pipe.transform(1048576)).toBe('1 MB');
  });
});
