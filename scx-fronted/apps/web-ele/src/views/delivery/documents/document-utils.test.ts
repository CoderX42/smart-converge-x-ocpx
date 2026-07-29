import { describe, expect, it } from 'vitest';

import {
  documentExtension,
  formatFileSize,
  parseCsv,
  previewKind,
  validateDocumentFile,
  validateExternalUrl,
} from './document-utils';

describe('integration document utilities', () => {
  it('validates upload extension, empty files and the 20MB limit', () => {
    expect(validateDocumentFile({ name: 'guide.pdf', size: 1024 })).toBe('');
    expect(validateDocumentFile({ name: 'guide.exe', size: 1024 })).toContain(
      '仅支持',
    );
    expect(validateDocumentFile({ name: 'empty.txt', size: 0 })).toContain(
      '空文件',
    );
    expect(
      validateDocumentFile({
        name: 'large.pdf',
        size: 20 * 1024 * 1024 + 1,
      }),
    ).toContain('20MB');
  });

  it('accepts http and https links but rejects credentials', () => {
    expect(validateExternalUrl('http://docs.example.com/guide')).toBe('');
    expect(validateExternalUrl('https://docs.example.com/guide')).toBe('');
    expect(
      validateExternalUrl('https://user:pass@docs.example.com/guide'),
    ).toContain('用户名或密码');
    expect(validateExternalUrl('file:///tmp/guide.pdf')).toContain(
      'HTTP 或 HTTPS',
    );
  });

  it('routes supported formats to safe local previewers', () => {
    const base = {
      documentVersion: 1,
      id: 'doc-1',
      mediaId: 'media-1',
      name: 'guide',
      status: 'ACTIVE' as const,
      storageType: 'OBJECT' as const,
      type: 'OTHER' as const,
      version: 0,
    };
    expect(previewKind({ ...base, originalFileName: 'guide.pdf' })).toBe('pdf');
    expect(previewKind({ ...base, originalFileName: 'guide.docx' })).toBe(
      'docx',
    );
    expect(previewKind({ ...base, originalFileName: 'guide.xlsx' })).toBe(
      'excel',
    );
    expect(previewKind({ ...base, originalFileName: 'guide.md' })).toBe(
      'markdown',
    );
    expect(previewKind({ ...base, originalFileName: 'legacy.doc' })).toBe(
      'fallback',
    );
  });

  it('parses quoted CSV cells and formats file metadata', () => {
    expect(parseCsv('name,note\r\nSCX,"a,b"\r\n')).toEqual([
      ['name', 'note'],
      ['SCX', 'a,b'],
    ]);
    expect(documentExtension('Guide.PDF')).toBe('pdf');
    expect(formatFileSize(1024)).toBe('1.0 KB');
  });
});
