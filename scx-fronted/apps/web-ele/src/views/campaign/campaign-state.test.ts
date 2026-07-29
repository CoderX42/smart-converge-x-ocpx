import { describe, expect, it } from 'vitest';

import { getCampaignUiActions } from './campaign-state';

describe('campaign status actions', () => {
  it('only permits editing and submission for a draft', () => {
    expect(getCampaignUiActions('DRAFT')).toEqual(['edit', 'submit']);
  });

  it('keeps approval actions inside the review center', () => {
    expect(getCampaignUiActions('PENDING_REVIEW')).toEqual([]);
    expect(getCampaignUiActions('PENDING_REVIEW', true)).toEqual([
      'approve',
      'reject',
    ]);
  });

  it('exposes delivery controls without review authority', () => {
    expect(getCampaignUiActions('DELIVERING')).toEqual(['pause', 'end']);
    expect(getCampaignUiActions('PAUSED')).toEqual(['resume', 'end']);
  });

  it('does not expose operations for an unknown or archived state', () => {
    expect(getCampaignUiActions('ARCHIVED')).toEqual([]);
    expect(getCampaignUiActions()).toEqual([]);
  });
});
