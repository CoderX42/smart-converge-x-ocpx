export type CampaignUiAction =
  | 'approve'
  | 'archive'
  | 'edit'
  | 'end'
  | 'pause'
  | 'publish'
  | 'reject'
  | 'resume'
  | 'submit';

const statusActions: Record<string, CampaignUiAction[]> = {
  APPROVED: ['publish'],
  DELIVERING: ['pause', 'end'],
  DRAFT: ['edit', 'submit'],
  ENDED: ['archive'],
  PAUSED: ['resume', 'end'],
  PENDING_REVIEW: ['approve', 'reject'],
};

export function getCampaignUiActions(
  status?: string,
  reviewCenter = false,
): CampaignUiAction[] {
  const actions = statusActions[status || ''] || [];
  return reviewCenter
    ? actions.filter((action) => action === 'approve' || action === 'reject')
    : actions.filter((action) => action !== 'approve' && action !== 'reject');
}
