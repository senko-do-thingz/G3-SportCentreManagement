import { EmptyState } from '../../../components/ui'

/**
 * Slot for the assistant chat. The accessible chat UI is NX-30 (Phat) and the scoped service is
 * NX-29 (Dat); neither exists yet and the member route/API contract is not defined, so this
 * placeholder calls nothing. NX-30 replaces the body of this component and keeps the page, the
 * opt-in, the route protection and the return navigation around it unchanged.
 */
export function AssistantHost() {
  return (
    <div data-slot="assistant-chat">
      <EmptyState
        title="The assistant chat is not available yet"
        description="The assistant is being built. When it is ready it appears here, and you can keep using the rest of the member area as usual."
      />
    </div>
  )
}
