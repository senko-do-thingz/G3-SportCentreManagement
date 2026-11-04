import { render, screen } from '@testing-library/react'
import { expect, it } from 'vitest'
import { Input } from './Input'

it('ID-05 keeps ISO native values and exposes formatted date and period hints', () => {
  render(<><Input label="Session date" type="date" value="2028-02-29" readOnly hint="Vietnam calendar" />
    <Input label="Reporting month" type="month" value="2028-02" readOnly />
    <Input label="Teaching week" type="week" value="2020-W53" readOnly /></>)
  expect(screen.getByLabelText('Session date')).toHaveValue('2028-02-29')
  expect(screen.getByLabelText('Session date')).toHaveAccessibleDescription('Vietnam calendar Selected date: 29/02/2028')
  expect(screen.getByText('Selected period: 01/02/2028 - 29/02/2028')).toBeInTheDocument()
  expect(screen.getByLabelText('Reporting month')).toHaveValue('2028-02')
  expect(screen.getByText('Selected week: 28/12/2020 - 03/01/2021')).toBeInTheDocument()
  expect(screen.getByLabelText('Teaching week')).toHaveValue('2020-W53')
})
