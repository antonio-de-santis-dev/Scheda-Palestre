import { PageHeader } from '../shared/components/PageHeader';
import { useCurrentUser } from '../auth/useAuth';

export function UserHomePage() {
  const { data: user } = useCurrentUser();
  return <PageHeader title={`Ciao ${user?.firstName ?? ''}`} subtitle="Benvenuto in GymPlanner." />;
}
