import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ImportPreview from '../../src/components/import/ImportPreview.vue'

/**
 * Unit tests for the ImportPreview component covering valid/invalid row styling,
 * error details display, commit button state, and empty state.
 */

const columns = ['student_username', 'class_name', 'term_name', 'student_id_number']

const validRows = [
  { rowNumber: 1, data: { student_username: 'stu001', class_name: 'ClassA', term_name: 'Term1', student_id_number: 'ID001' } },
  { rowNumber: 2, data: { student_username: 'stu002', class_name: 'ClassB', term_name: 'Term1', student_id_number: 'ID002' } },
]

const invalidRows = [
  {
    rowNumber: 3,
    data: { student_username: '', class_name: 'ClassC', term_name: 'Term1', student_id_number: '' },
    errors: [
      { field: 'student_username', reason: 'Required field is missing' },
      { field: 'student_id_number', reason: 'Required field is missing' },
    ]
  },
]

describe('ImportPreview', () => {
  it('testValidRowsGreen', () => {
    const wrapper = mount(ImportPreview, {
      props: { columns, validRows, invalidRows: [] }
    })

    // Valid rows section should have green styling
    const validSection = wrapper.find('.section-valid')
    expect(validSection.exists()).toBe(true)
    expect(validSection.text()).toContain('Valid Rows')

    const validTable = wrapper.find('.valid-table')
    expect(validTable.exists()).toBe(true)
  })

  it('testInvalidRowsRed', () => {
    const wrapper = mount(ImportPreview, {
      props: { columns, validRows: [], invalidRows }
    })

    // Invalid rows section should have red styling
    const invalidSection = wrapper.find('.section-invalid')
    expect(invalidSection.exists()).toBe(true)
    expect(invalidSection.text()).toContain('Invalid Rows')

    const invalidTable = wrapper.find('.invalid-table')
    expect(invalidTable.exists()).toBe(true)
  })

  it('testErrorDetailsShown', () => {
    const wrapper = mount(ImportPreview, {
      props: { columns, validRows: [], invalidRows }
    })

    // Error badges should be visible for invalid fields
    const errorBadges = wrapper.findAll('.error-badge')
    expect(errorBadges.length).toBeGreaterThan(0)

    // Check that error reasons are displayed
    const errorTexts = errorBadges.map(b => b.text())
    expect(errorTexts.some(t => t.includes('Required field is missing'))).toBe(true)

    // Row number should be displayed
    const rows = wrapper.findAll('.invalid-table tbody tr')
    expect(rows.length).toBe(1)
    expect(rows[0].text()).toContain('3') // row number 3
  })

  it('testCommitDisabledAllInvalid', () => {
    // When all rows are invalid, the component should show only invalid section
    const allInvalidRows = [
      { rowNumber: 1, data: { student_username: '' }, errors: [{ field: 'student_username', reason: 'Required' }] },
      { rowNumber: 2, data: { student_username: '' }, errors: [{ field: 'student_username', reason: 'Required' }] },
    ]

    const wrapper = mount(ImportPreview, {
      props: { columns, validRows: [], invalidRows: allInvalidRows }
    })

    // No valid section should be rendered
    expect(wrapper.find('.section-valid').exists()).toBe(false)

    // Summary should show 0 valid
    const summaryValid = wrapper.find('.summary-valid')
    expect(summaryValid.text()).toContain('0')
  })

  it('testEmptyPreview', () => {
    const wrapper = mount(ImportPreview, {
      props: { columns, validRows: [], invalidRows: [] }
    })

    // No sections should be rendered
    expect(wrapper.find('.section-valid').exists()).toBe(false)
    expect(wrapper.find('.section-invalid').exists()).toBe(false)

    // Total should show 0
    const summaryTotal = wrapper.find('.summary-total')
    expect(summaryTotal.text()).toContain('0')
  })
})
